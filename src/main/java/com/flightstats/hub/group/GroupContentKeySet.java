package com.flightstats.hub.group;

import com.flightstats.hub.model.ContentKey;
import com.google.inject.Inject;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupContentKeySet {
    private final static Logger logger = LoggerFactory.getLogger(GroupContentKeySet.class);

    private final CuratorFramework curator;

    @Inject
    public GroupContentKeySet(CuratorFramework curator) {
        this.curator = curator;
    }

    public void add(String groupName, ContentKey key) {
        String path = getPath(groupName, key);
        try {
            curator.create().creatingParentsIfNeeded().forPath(path);
        } catch (KeeperException.NodeExistsException ignore) {
            logger.info("node exists " + path);
        } catch (Exception e) {
            logger.warn("unable to create " + path , e);
        }
    }

    public void remove(String groupName, ContentKey key) {
        String path = getPath(groupName, key);
        try {
            curator.delete().forPath(path);
        } catch (Exception e) {
            logger.warn("unable to delete " + path , e);
        }
    }

    public Set<ContentKey> getSet(String groupName) {
        String path = getPath(groupName);
        Set<ContentKey> keys = new HashSet<>();
        try {
            List<String> strings = curator.getChildren().forPath(path);
            for (String string : strings) {
                keys.add(ContentKey.fromZk(string));
            }
        } catch (KeeperException.NoNodeException e) {
            logger.info("no node for {}", path);
        } catch (Exception e) {
            logger.warn("unable to get set " + path , e);
        }
        return keys;
    }

    private String getPath(String groupName) {
        return "/GroupInFlight/" + groupName;
    }

    private String getPath(String groupName, ContentKey key) {
        return getPath(groupName) + "/" + key.toZk();
    }

    public void delete(String groupName)  {
        String path = getPath(groupName);
        try {
            curator.delete().deletingChildrenIfNeeded().forPath(path);
        } catch (Exception e) {
            logger.warn("unable to delete {} {}", path, e.getMessage());
        }
    }
}
