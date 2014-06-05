package com.flightstats.hub.dao;

import com.codahale.metrics.annotation.Timed;
import com.flightstats.hub.model.ContentKey;
import com.flightstats.hub.model.SequenceContentKey;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Longs;
import com.google.inject.Inject;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SequenceLastUpdatedDao implements LastUpdatedDao {
    private final static Logger logger = LoggerFactory.getLogger(SequenceLastUpdatedDao.class);

    private final CuratorFramework curator;

    @Inject
    public SequenceLastUpdatedDao(CuratorFramework curator) {
        this.curator = curator;
    }

    @Override
    public void update(String channelName, ContentKey key) {
        setLastUpdateKey(channelName, key);
    }

    public void initialize(String channelName) {
        try {
            byte[] data = Longs.toByteArray(SequenceContentKey.START_VALUE);
            curator.create().creatingParentsIfNeeded().forPath(getPath(channelName), data);
        } catch (KeeperException.NodeExistsException ingore) {
            //ignore this
        } catch (Exception e) {
            logger.warn("unable to seed " + channelName, e);
        }
    }

    @Timed(name = "sequence.setLastUpdated")
    private void setLastUpdateKey(String channelName, ContentKey key) {
        try {
            SequenceContentKey sequence = (SequenceContentKey) key;
            byte[] bytes = Longs.toByteArray(sequence.getSequence());
            String path = getPath(channelName);
            int attempts = 0;
            while (attempts < 3) {
                LastUpdated existing = getFromZK(channelName);
                if (sequence.getSequence() > existing.value) {
                    if (setValue(path, bytes, existing)) {
                        return;
                    }
                } else {
                    return;
                }
                attempts++;
            }
        } catch (Exception e) {
            logger.warn("unable to set " + channelName + " lastUpdated to " + key, e);
        }
    }

    private boolean setValue(String path, byte[] bytes, LastUpdated existing) throws Exception {
        try {
            if (existing.version == -1) {
                curator.create().creatingParentsIfNeeded().forPath(path, bytes);
            } else {
                curator.setData().withVersion(existing.version).forPath(path, bytes);
            }
            return true;
        } catch (Exception e) {
            logger.info("what happened? " + path, e);
            return false;
        }
    }

    @VisibleForTesting
    LastUpdated getFromZK(String channelName) {
        try {
            Stat stat = new Stat();
            byte[] bytes = curator.getData().storingStatIn(stat).forPath(getPath(channelName));
            return new LastUpdated(Longs.fromByteArray(bytes), stat.getVersion());
        } catch (Exception e) {
            logger.info("unable to get value " + channelName + " " + e.getMessage(), e);
            return new LastUpdated(SequenceContentKey.START_VALUE, -1);
        }
    }

    @Override
    public ContentKey getLastUpdated(final String channelName) {
        return new SequenceContentKey(getLongValue(channelName));
    }

    @Timed(name = "sequence.getLastUpdated")
    public long getLongValue(String channelName) {
        return getFromZK(channelName).value;
    }

    @Override
    public void delete(String channelName) {
        String path = getPath(channelName);
        try {
            curator.delete().deletingChildrenIfNeeded().forPath(path);
        } catch (Exception e) {
            logger.warn("unable to delete key " + channelName);
        }
    }

    public String getPath(String channelName) {
        return "/lastUpdated/" + channelName;
    }

    class LastUpdated {
        long value;
        int version;

        private LastUpdated(long value, int version) {
            this.value = value;
            this.version = version;
        }
    }

}