package com.flightstats.hub.dao;

import com.diffplug.common.base.Errors;
import com.flightstats.hub.app.HubProperties;
import com.flightstats.hub.app.HubServices;
import com.flightstats.hub.dao.aws.MultiPartParser;
import com.flightstats.hub.exception.FailedQueryException;
import com.flightstats.hub.metrics.ActiveTraces;
import com.flightstats.hub.metrics.Traces;
import com.flightstats.hub.model.*;
import com.flightstats.hub.time.TimeService;
import com.flightstats.hub.util.RuntimeInterruptedException;
import com.flightstats.hub.util.Sleeper;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Performs common logic for all ContentServices.
 */
public class CommonContentService implements ContentService {
    private final static Logger logger = LoggerFactory.getLogger(CommonContentService.class);

    private final Integer shutdown_wait_seconds = HubProperties.getProperty("app.shutdown_wait_seconds", 5);
    private final AtomicInteger inFlight = new AtomicInteger();
    private static final ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("ContentService-%d").build());

    @Inject
    @Named(ContentService.IMPL)
    private ContentService contentService;
    @Inject
    private TimeService timeService;

    public CommonContentService() {
        HubServices.registerPreStop(new CommonContentServiceShutdown());
    }

    public static SortedSet<ContentKey> query(Function<ContentDao, SortedSet<ContentKey>> daoQuery, List<ContentDao> contentDaos) {
        try {
            QueryResult queryResult = new QueryResult(contentDaos.size());
            CountDownLatch latch = new CountDownLatch(contentDaos.size());
            Traces traces = ActiveTraces.getLocal();
            String threadName = Thread.currentThread().getName();
            for (ContentDao contentDao : contentDaos) {
                executorService.submit(() -> {
                    Thread.currentThread().setName(contentDao.getClass().getSimpleName() + "|" + threadName);
                    ActiveTraces.setLocal(traces);
                    try {
                        queryResult.addKeys(daoQuery.apply(contentDao));
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(118, TimeUnit.SECONDS);
            if (queryResult.hadSuccess()) {
                return queryResult.getContentKeys();
            } else {
                traces.add("unable to complete query ", queryResult);
                throw new FailedQueryException("unable to complete query " + queryResult + " " + threadName);
            }
        } catch (InterruptedException e) {
            throw new RuntimeInterruptedException(e);
        }
    }

    private <X> X inFlight(Supplier<X> supplier) {
        try {
            inFlight.incrementAndGet();
            return supplier.get();
        } finally {
            inFlight.decrementAndGet();
        }
    }

    @Override
    public ContentKey insert(String channelName, Content content) throws Exception {
        return inFlight(Errors.rethrow().wrap(() -> contentService.insert(channelName, content)));
    }

    @Override
    public Collection<ContentKey> insert(BulkContent bulkContent) throws Exception {
        return inFlight(Errors.rethrow().wrap(() -> {
            MultiPartParser multiPartParser = new MultiPartParser(bulkContent);
            multiPartParser.parse(timeService);
            return contentService.insert(bulkContent);
        }));
    }

    @Override
    public boolean historicalInsert(String channelName, Content content) throws Exception {
        return inFlight(Errors.rethrow().wrap(() -> contentService.historicalInsert(channelName, content)));
    }

    @Override
    public Optional<Content> get(String channelName, ContentKey key) {
        return contentService.get(channelName, key);
    }

    @Override
    public void get(String channel, SortedSet<ContentKey> keys, Consumer<Content> callback) {
        contentService.get(channel, keys, callback);
    }

    @Override
    public Collection<ContentKey> queryByTime(TimeQuery timeQuery) {
        return contentService.queryByTime(timeQuery);
    }

    @Override
    public void delete(String channelName) {
        contentService.delete(channelName);
    }

    @Override
    public Collection<ContentKey> queryDirection(DirectionQuery query) {
        return contentService.queryDirection(query);
    }

    @Override
    public Optional<ContentKey> getLatest(String channel, ContentKey limitKey, Traces traces, boolean stable) {
        return contentService.getLatest(channel, limitKey, traces, stable);
    }

    @Override
    public void deleteBefore(String name, ContentKey limitKey) {
        contentService.deleteBefore(name, limitKey);
    }

    @Override
    public void notify(ChannelConfig newConfig, ChannelConfig oldConfig) {
        contentService.notify(newConfig, oldConfig);
    }

    private void waitForInFlight() {
        logger.info("waiting for in-flight to complete " + inFlight.get());
        long start = System.currentTimeMillis();
        while (inFlight.get() > 0) {
            logger.info("still waiting for in-flight to complete " + inFlight.get());
            Sleeper.sleep(1000);
            if (System.currentTimeMillis() > (start + shutdown_wait_seconds * 1000)) {
                break;
            }
        }
        logger.info("completed waiting for in-flight to complete " + inFlight.get());
    }


    private class CommonContentServiceShutdown extends AbstractIdleService {
        @Override
        protected void startUp() throws Exception {
            //do nothing
        }

        @Override
        protected void shutDown() throws Exception {
            waitForInFlight();
        }
    }


}
