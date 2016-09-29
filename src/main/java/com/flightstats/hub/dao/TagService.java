package com.flightstats.hub.dao;

import com.flightstats.hub.channel.ChannelEarliestResource;
import com.flightstats.hub.metrics.ActiveTraces;
import com.flightstats.hub.metrics.Traces;
import com.flightstats.hub.model.*;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class TagService {
    private final static Logger logger = LoggerFactory.getLogger(TagService.class);

    @Inject
    private ChannelService channelService;

    public Iterable<ChannelConfig> getChannels(String tag) {
        Collection<ChannelConfig> channelConfigs = channelService.getChannels(tag);
        ActiveTraces.getLocal().add("TagService.getChannels", channelConfigs);
        return channelConfigs;
    }

    public Iterable<String> getTags() {
        return channelService.getTags();
    }

    public SortedSet<ChannelContentKey> queryByTime(TimeQuery timeQuery) {
        Iterable<ChannelConfig> channels = getChannels(timeQuery.getTagName());
        SortedSet<ChannelContentKey> orderedKeys = Collections.synchronizedSortedSet(new TreeSet<>());
        for (ChannelConfig channel : channels) {
            Collection<ContentKey> contentKeys = channelService.queryByTime(timeQuery.withChannelName(channel.getName()));
            for (ContentKey contentKey : contentKeys) {
                orderedKeys.add(new ChannelContentKey(channel.getName(), contentKey));
            }
        }
        return orderedKeys;
    }

    public SortedSet<ChannelContentKey> getKeys(DirectionQuery query) {
        Iterable<ChannelConfig> channels = getChannels(query.getTagName());
        SortedSet<ChannelContentKey> orderedKeys = Collections.synchronizedSortedSet(new TreeSet<>());
        Traces traces = ActiveTraces.getLocal();
        for (ChannelConfig channel : channels) {
            traces.add("TagService.getKeys", channel.getName());
            Collection<ContentKey> contentKeys = channelService.getKeys(query.withChannelName(channel.getName()));
            traces.add("TagService.getKeys size", channel.getName(), contentKeys.size());
            for (ContentKey contentKey : contentKeys) {
                orderedKeys.add(new ChannelContentKey(channel.getName(), contentKey));
            }
        }

        Stream<ChannelContentKey> stream = orderedKeys.stream();
        if (!query.isNext()) {
            Collection<ChannelContentKey> contentKeys = new TreeSet<>(Collections.reverseOrder());
            contentKeys.addAll(orderedKeys);
            stream = contentKeys.stream();
        }

        return stream
                .limit(query.getCount())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public Optional<ChannelContentKey> getLatest(String tag, boolean stable, boolean trace) {
        Iterable<ChannelConfig> channels = getChannels(tag);
        SortedSet<ChannelContentKey> orderedKeys = Collections.synchronizedSortedSet(new TreeSet<>());
        for (ChannelConfig channel : channels) {
            Optional<ContentKey> contentKey = channelService.getLatest(channel.getName(), stable, trace);
            if (contentKey.isPresent()) {
                orderedKeys.add(new ChannelContentKey(channel.getName(), contentKey.get()));
            }
        }
        if (orderedKeys.isEmpty()) {
            return Optional.absent();
        } else {
            return Optional.of(orderedKeys.last());
        }
    }

    public SortedSet<ChannelContentKey> getEarliest(String tag, int count, boolean stable, boolean trace) {
        Iterable<ChannelConfig> channels = getChannels(tag);
        Traces traces = ActiveTraces.getLocal();
        traces.add("TagService.getEarliest", tag);
        SortedSet<ChannelContentKey> orderedKeys = Collections.synchronizedSortedSet(new TreeSet<>());
        for (ChannelConfig channel : channels) {
            DirectionQuery query = ChannelEarliestResource.getDirectionQuery(channel.getName(), count, stable, channelService);
            Collection<ContentKey> contentKeys = channelService.getKeys(query);
            for (ContentKey contentKey : contentKeys) {
                orderedKeys.add(new ChannelContentKey(channel.getName(), contentKey));
            }
        }
        if (trace) {
            traces.log(logger);
        }
        traces.add("TagService.getEarliest completed", orderedKeys);
        return orderedKeys;
    }

    public Optional<Content> getValue(Request request) {
        Iterable<ChannelConfig> channels = getChannels(request.getTag());
        for (ChannelConfig channel : channels) {
            Optional<Content> value = channelService.get(request.withChannel(channel.getName()));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.absent();
    }

    public ChannelService getChannelService() {
        return channelService;
    }
}
