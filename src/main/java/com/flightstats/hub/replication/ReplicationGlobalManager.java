package com.flightstats.hub.replication;

import com.flightstats.hub.cluster.WatchManager;
import com.flightstats.hub.cluster.Watcher;
import com.flightstats.hub.dao.ChannelService;
import com.flightstats.hub.model.BuiltInTag;
import com.flightstats.hub.model.ChannelConfig;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.curator.framework.api.CuratorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.flightstats.hub.app.HubServices.TYPE;
import static com.flightstats.hub.app.HubServices.register;

@Singleton
public class ReplicationGlobalManager {
    private final static Logger logger = LoggerFactory.getLogger(ReplicationGlobalManager.class);
    private static final String REPLICATOR_WATCHER_PATH = "/replicator/watcher";

    private ChannelService channelService;
    private WatchManager watchManager;

    private final Map<String, ChannelReplicator> channelReplicatorMap = new HashMap<>();
    private final Map<String, GlobalReplicator> globalReplicatorMap = new HashMap<>();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("ReplicationGlobalManager").build());
    private final ExecutorService executorPool = Executors.newFixedThreadPool(20,
            new ThreadFactoryBuilder().setNameFormat("ReplicationGlobalManager-%d").build());

    @Inject
    public ReplicationGlobalManager(ChannelService channelService, WatchManager watchManager) {
        this.channelService = channelService;
        this.watchManager = watchManager;
        register(new ReplicationGlobalService(), TYPE.AFTER_HEALTHY_START, TYPE.PRE_STOP);
    }

    private void startManager() {
        logger.info("starting");
        ReplicationGlobalManager manager = this;
        watchManager.register(new Watcher() {
            @Override
            public void callback(CuratorEvent event) {
                executor.submit(manager::globalAndChannels);
            }

            @Override
            public String getPath() {
                return REPLICATOR_WATCHER_PATH;
            }
        });
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat("ReplicationGlobalManager-hourly").build());
        scheduledExecutorService.scheduleAtFixedRate(manager::globalAndChannels, 0, 1, TimeUnit.HOURS);
    }

    private void globalAndChannels() {
        if (stopped.get()) {
            logger.info("replication stopped");
            return;
        }
        logger.info("starting checks for replication and global");
        replicateGlobal();
        replicateChannels();
        logger.info("completed checks for replication and global");
    }

    private synchronized void replicateGlobal() {
        Set<String> replicators = new HashSet<>();
        Iterable<ChannelConfig> globalChannels = channelService.getChannels(BuiltInTag.GLOBAL.toString(), false);
        logger.info("replicating global channels {}", globalChannels);
        for (ChannelConfig channel : globalChannels) {
            logger.info("replicating global channel {}", channel);
            if (channel.isGlobalMaster()) {
                try {
                    processGlobal(replicators, channel);
                } catch (Exception e) {
                    logger.warn("unable to do global replication" + channel, e);
                }
            }
        }
        executorPool.submit(() -> stopAndRemove(replicators, globalReplicatorMap));
        logger.info("completed global");
    }

    private void processGlobal(Set<String> replicators, ChannelConfig channel) {
        for (String satellite : channel.getGlobal().getSatellites()) {
            logger.info("creating satellite {} {}", satellite, channel.getName());
            GlobalReplicator replicator = new GlobalReplicator(channel, satellite);
            String replicatorKey = replicator.getKey();
            replicators.add(replicatorKey);
            if (globalReplicatorMap.containsKey(replicatorKey)) {
                globalReplicatorMap.get(replicatorKey).start();
            } else {
                executorPool.submit(() -> {
                    try {
                        globalReplicatorMap.put(replicatorKey, replicator);
                        replicator.start();
                    } catch (Exception e) {
                        globalReplicatorMap.remove(replicatorKey);
                        logger.warn("unexpected global issue " + replicatorKey + " " + channel, e);
                    }
                });
            }
        }
    }

    private synchronized void replicateChannels() {
        Set<String> replicators = new HashSet<>();
        Iterable<ChannelConfig> replicatedChannels = channelService.getChannels(BuiltInTag.REPLICATED.toString(), false);
        logger.info("replicating channels {}", replicatedChannels);
        for (ChannelConfig channel : replicatedChannels) {
            logger.info("replicating channel {}", channel.getName());
            try {
                processChannel(replicators, channel);
            } catch (Exception e) {
                logger.warn("error trying to replicate " + channel, e);
            }
        }
        executorPool.submit(() -> stopAndRemove(replicators, channelReplicatorMap));

    }

    private void processChannel(Set<String> replicators, ChannelConfig channel) {
        replicators.add(channel.getName());
        if (channelReplicatorMap.containsKey(channel.getName())) {
            ChannelReplicator replicator = channelReplicatorMap.get(channel.getName());
            if (!replicator.getChannel().getReplicationSource().equals(channel.getReplicationSource())) {
                logger.info("changing replication source from {} to {}",
                        replicator.getChannel().getReplicationSource(), channel.getReplicationSource());
                replicator.stop();
                startReplication(channel);
            } else {
                replicator.start();
            }
        } else {
            startReplication(channel);
        }
    }

    private void stopAndRemove(Set<String> replicators, Map<String, ? extends Replicator> replicatorMap) {
        Set<String> toStop = new HashSet<>(replicatorMap.keySet());
        toStop.removeAll(replicators);
        logger.info("stopping replicators {}", toStop);
        for (String nameToStop : toStop) {
            logger.info("stopping {}", nameToStop);
            Replicator replicator = replicatorMap.remove(nameToStop);
            replicator.stop();
        }
    }

    private void startReplication(ChannelConfig channel) {
        executorPool.submit(() -> {
            try {
                logger.debug("starting replication of " + channel);
                ChannelReplicator channelReplicator = new ChannelReplicator(channel);
                channelReplicatorMap.put(channel.getName(), channelReplicator);
                channelReplicator.start();
            } catch (Exception e) {
                channelReplicatorMap.remove(channel.getName());
                logger.warn("unexpected replication issue " + channel, e);
            }
        });
    }

    public void notifyWatchers() {
        watchManager.notifyWatcher(REPLICATOR_WATCHER_PATH);
    }

    private class ReplicationGlobalService extends AbstractIdleService {

        @Override
        protected void startUp() throws Exception {
            startManager();
        }

        @Override
        protected void shutDown() throws Exception {
            stopped.set(true);
        }

    }

}
