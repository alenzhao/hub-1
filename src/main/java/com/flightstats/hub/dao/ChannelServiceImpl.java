package com.flightstats.hub.dao;

import com.flightstats.hub.dao.timeIndex.TimeIndexProcessor;
import com.flightstats.hub.model.*;
import com.flightstats.hub.replication.ChannelReplicator;
import com.flightstats.hub.replication.ReplicationValidator;
import com.flightstats.hub.service.CreateChannelValidator;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 *
 */
public class ChannelServiceImpl implements ChannelService {

    private final static Logger logger = LoggerFactory.getLogger(ChannelServiceImpl.class);

    private final ContentServiceFinder contentServiceFinder;
    private final ChannelConfigurationDao channelConfigurationDao;
    private final CreateChannelValidator createChannelValidator;
    private final TimeIndexProcessor timeIndexProcessor;
    private final ChannelReplicator channelReplicator;
    private final ReplicationValidator replicationValidator;
    private final ContentService missingDao = new ContentService() {
        @Override
        public void createChannel(ChannelConfiguration configuration) { }

        @Override
        public void updateChannel(ChannelConfiguration configuration) { }

        @Override
        public InsertedContentKey insert(ChannelConfiguration configuration, Content content) {
            return null;
        }

        @Override
        public Optional<LinkedContent> getValue(String channelName, String id) {
            return Optional.absent();
        }

        @Override
        public Optional<ContentKey> findLastUpdatedKey(String channelName) {
            return Optional.absent();
        }

        @Override
        public Collection<ContentKey> getKeys(String channelName, DateTime dateTime) {
            return Collections.emptyList();
        }

        @Override
        public void delete(String channelName) { }

    };

    @Inject
    public ChannelServiceImpl(ContentServiceFinder contentServiceFinder, ChannelConfigurationDao channelConfigurationDao,
                              CreateChannelValidator createChannelValidator, TimeIndexProcessor timeIndexProcessor,
                              ChannelReplicator channelReplicator, ReplicationValidator replicationValidator) {
        this.contentServiceFinder = contentServiceFinder;
        this.channelConfigurationDao = channelConfigurationDao;
        this.createChannelValidator = createChannelValidator;
        this.timeIndexProcessor = timeIndexProcessor;
        this.channelReplicator = channelReplicator;
        this.replicationValidator = replicationValidator;
    }

    private ContentService getContentService(String channelName){
        ChannelConfiguration channelConfiguration = channelConfigurationDao.getChannelConfiguration(channelName);
        if (null == channelConfiguration) {
            logger.info("did not find config for " + channelName);
            return missingDao;
        }
        return contentServiceFinder.getContentService(channelConfiguration);
    }

    @Override
    public boolean channelExists(String channelName) {
        return channelConfigurationDao.channelExists(channelName);
    }

    @Override
    public ChannelConfiguration createChannel(ChannelConfiguration configuration) {
        createChannelValidator.validate(configuration);
        configuration = ChannelConfiguration.builder().withChannelConfiguration(configuration).build();
        contentServiceFinder.getContentService(configuration).createChannel(configuration);
        return channelConfigurationDao.createChannel(configuration);
    }

    @Override
    public InsertedContentKey insert(String channelName, Content content) {
        if (!content.getContentKey().isPresent()) {
            replicationValidator.checkIfReplicating(channelName);
        }
        ChannelConfiguration configuration = channelConfigurationDao.getChannelConfiguration(channelName);
        return getContentService(channelName).insert(configuration, content);
    }

    @Override
    public Optional<LinkedContent> getValue(String channelName, String id) {
        return getContentService(channelName).getValue(channelName, id);
    }

    @Override
    public ChannelConfiguration getChannelConfiguration(String channelName) {
        return channelConfigurationDao.getChannelConfiguration(channelName);
    }

    @Override
    public Iterable<ChannelConfiguration> getChannels() {
        return channelConfigurationDao.getChannels();
    }

    @Override
    public Iterable<ChannelConfiguration> getChannels(String tag) {
        Collection<ChannelConfiguration> matchingChannels = new ArrayList<>();
        Iterable<ChannelConfiguration> channels = getChannels();
        for (ChannelConfiguration channel : channels) {
            if (channel.getTags().contains(tag)) {
                matchingChannels.add(channel);
            }
        }
        return matchingChannels;
    }

    @Override
    public Iterable<String> getTags() {
        Collection<String> matchingChannels = new HashSet<>();
        Iterable<ChannelConfiguration> channels = getChannels();
        for (ChannelConfiguration channel : channels) {
            matchingChannels.addAll(channel.getTags());
        }
        return matchingChannels;
    }

    @Override
    public Optional<ContentKey> findLastUpdatedKey(String channelName) {
        return getContentService(channelName).findLastUpdatedKey(channelName);
    }

    @Override
    public boolean isHealthy() {
        return channelConfigurationDao.isHealthy();
    }

    @Override
    public ChannelConfiguration updateChannel(ChannelConfiguration configuration) {
        configuration = ChannelConfiguration.builder().withChannelConfiguration(configuration).build();
        contentServiceFinder.getContentService(configuration).updateChannel(configuration);
        channelConfigurationDao.updateChannel(configuration);
        return configuration;
    }

    @Override
    public Collection<ContentKey> getKeys(String channelName, DateTime dateTime) {
        return getContentService(channelName).getKeys(channelName, dateTime);
    }

    @Override
    public boolean delete(String channelName) {
        if (!channelConfigurationDao.channelExists(channelName)) {
            return false;
        }
        getContentService(channelName).delete(channelName);
        channelConfigurationDao.delete(channelName);
        timeIndexProcessor.delete(channelName);
        channelReplicator.delete(channelName);
        return true;
    }
}
