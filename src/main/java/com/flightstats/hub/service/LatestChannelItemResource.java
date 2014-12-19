package com.flightstats.hub.service;

import com.flightstats.hub.app.config.metrics.EventTimed;
import com.flightstats.hub.dao.ChannelService;
import com.flightstats.hub.model.ContentKey;
import com.flightstats.hub.model.DirectionQuery;
import com.flightstats.hub.util.TimeUtil;
import com.google.inject.Inject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.SEE_OTHER;

@Path("/channel/{channelName: .*}/latest")
public class LatestChannelItemResource {

    private final UriInfo uriInfo;
    private final ChannelService channelService;

    @Inject
    public LatestChannelItemResource(UriInfo uriInfo, ChannelService channelService) {
        this.uriInfo = uriInfo;
        this.channelService = channelService;
    }

    @GET
    @EventTimed(name = "channel.ALL.latest.get")
    public Response getLatest(@PathParam("channelName") String channelName,
                              @QueryParam("stable") @DefaultValue("true") boolean stable) {
        DirectionQuery query = DirectionQuery.builder()
                .channelName(channelName)
                .contentKey(new ContentKey(TimeUtil.time(stable), "ZZZZZ"))
                .next(false)
                .stable(stable)
                .count(1).build();
        Collection<ContentKey> keys = channelService.getKeys(query);
        if (keys.isEmpty()) {
            return Response.status(NOT_FOUND).build();
        }
        Response.ResponseBuilder builder = Response.status(SEE_OTHER);
        ContentKey foundKey = keys.iterator().next();
        URI uri = URI.create(uriInfo.getBaseUri() + "channel/" + channelName + "/" + foundKey.toUrl());
        builder.location(uri);
        return builder.build();
    }

}
