package com.flightstats.hub.channel;

import com.flightstats.hub.app.HubProperties;
import com.flightstats.hub.dao.ChannelService;
import com.flightstats.hub.exception.ConflictException;
import com.flightstats.hub.exception.ForbiddenRequestException;
import com.flightstats.hub.exception.InvalidRequestException;
import com.flightstats.hub.model.ChannelConfig;
import com.flightstats.hub.model.GlobalConfig;
import com.google.common.base.Strings;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChannelValidatorTest {

    private ChannelService channelService;
    private ChannelValidator validator;

    @Before
    public void setUp() throws Exception {
        channelService = mock(ChannelService.class);
        validator = new ChannelValidator(channelService);
        when(channelService.channelExists(any(String.class))).thenReturn(false);
        HubProperties.setProperty("hub.protect.channels", "false");
    }

    @Test
    public void testAllGood() throws Exception {
        validator.validate(ChannelConfig.builder().name(Strings.repeat("A", 48)).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testTooLong() throws Exception {
        validator.validate(ChannelConfig.builder().name(Strings.repeat("A", 49)).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testChannelNameNull() throws Exception {
        validator.validate(ChannelConfig.builder().name(null).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testChannelNameEmpty() throws Exception {
        validator.validate(ChannelConfig.builder().name("").build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testChannelNameBlank() throws Exception {
        validator.validate(ChannelConfig.builder().name("  ").build(), null, false);
    }

    @Test(expected = ConflictException.class)
    public void testChannelExists() throws Exception {
        String channelName = "achannel";
        when(channelService.channelExists(channelName)).thenReturn(true);
        ChannelConfig channelConfig = ChannelConfig.builder().name(channelName).build();
        validator.validate(channelConfig, null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testInvalidSpaceCharacter() throws Exception {
        validator.validate(ChannelConfig.builder().name("my chan").build(), null, false);
    }

    @Test
    public void testValidUnderscore() throws Exception {
        validator.validate(ChannelConfig.builder().name("my_chan").build(), null, false);
    }

    @Test
    public void testValidHyphen() throws Exception {
        validator.validate(ChannelConfig.builder().name("my-chan").build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testInvalidCharacter() throws Exception {
        validator.validate(ChannelConfig.builder().name("my#chan").build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testInvalidChannelTtl() throws Exception {
        validator.validate(ChannelConfig.builder()
                .name("mychan")
                .ttlDays(10)
                .maxItems(10)
                .build(), null, false);
    }

    @Test
    public void testDescription1024() throws Exception {
        validator.validate(ChannelConfig.builder().name("desc").description(Strings.repeat("A", 1024)).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testDescriptionTooBig() throws Exception {
        validator.validate(ChannelConfig.builder().name("toobig").description(Strings.repeat("A", 1025)).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testTagSpace() throws Exception {
        validator.validate(ChannelConfig.builder().name("space").tags(Arrays.asList("s p a c e")).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testTagUnderscore() throws Exception {
        validator.validate(ChannelConfig.builder().name("underscore").tags(Arrays.asList("under_score")).build(), null, false);
    }

    @Test
    public void testTagValid() throws Exception {
        validator.validate(ChannelConfig.builder().name("valid1").tags(Arrays.asList("abcdefghijklmnopqrstuvwxyz")).build(), null, false);
        validator.validate(ChannelConfig.builder().name("valid2").tags(Arrays.asList("ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789")).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testTagTooLong() throws Exception {
        validator.validate(ChannelConfig.builder().name("tooLongTag")
                .tags(Arrays.asList(Strings.repeat("A", 49))).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testTooManyTags() throws Exception {
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tags.add("" + i);
        }
        validator.validate(ChannelConfig.builder().name("tooManyTags")
                .tags(tags).build(), null, false);
    }

    @Test
    public void testAllowColonAndDash() throws Exception {
        validator.validate(ChannelConfig.builder().name("colon").tags(Arrays.asList("a:b")).build(), null, false);
        validator.validate(ChannelConfig.builder().name("dash").tags(Arrays.asList("a-b")).build(), null, false);
        validator.validate(ChannelConfig.builder().name("colondash").tags(Arrays.asList("a-b:c")).build(), null, false);
    }

    @Test
    public void testReplicatedTag() {
        ChannelConfig config = ChannelConfig.builder().replicationSource("http://nowhere").build();
        assertTrue(config.getTags().contains("replicated"));
    }

    @Test
    public void testGlobalTag() {
        ChannelConfig config = ChannelConfig.builder().global(new GlobalConfig()).build();
        assertTrue(config.getTags().contains("global"));
    }

    @Test
    public void testHistoricalTag() {
        ChannelConfig config = ChannelConfig.builder().historical(true).build();
        assertTrue(config.getTags().contains("historical"));
    }

    public void testOwner() throws Exception {
        validator.validate(ChannelConfig.builder().name("A").owner(Strings.repeat("A", 48)).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testTooLongOwner() throws Exception {
        validator.validate(ChannelConfig.builder().name("A").owner(Strings.repeat("A", 49)).build(), null, false);
    }

    @Test
    public void testValidStorage() {
        validator.validate(ChannelConfig.builder().name("storage").storage(ChannelConfig.SINGLE).build(), null, false);
        validator.validate(ChannelConfig.builder().name("storage").storage("batch").build(), null, false);
        validator.validate(ChannelConfig.builder().name("storage").storage("BoTh").build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testInvalidStorage() {
        validator.validate(ChannelConfig.builder().name("storage").storage("stuff").build(), null, false);
    }

    @Test
    public void testGlobal() {
        GlobalConfig globalConfig = getGlobalConfig();
        validator.validate(ChannelConfig.builder()
                .name("global")
                .global(globalConfig).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testNoSatellite() {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMaster("http://master");
        validator.validate(ChannelConfig.builder()
                .name("global")
                .global(globalConfig).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testNoMaster() {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.addSatellite("http://master");
        validator.validate(ChannelConfig.builder()
                .name("global")
                .global(globalConfig).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testSatelliteSame() {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMaster("http://master");
        globalConfig.addSatellite("http://master/");
        validator.validate(ChannelConfig.builder()
                .name("global")
                .global(globalConfig).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testMasterUrl() throws MalformedURLException {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMaster("http:/master");
        globalConfig.addSatellite("http://satellite");
        validator.validate(ChannelConfig.builder()
                .name("global")
                .global(globalConfig).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testSatelliteUrl() throws MalformedURLException {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMaster("http:/master");
        globalConfig.addSatellite("http://satellite");
        globalConfig.addSatellite("ftp://satellite2");
        validator.validate(ChannelConfig.builder()
                .name("global")
                .global(globalConfig).build(), null, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testHistoricalSwitch() throws Exception {
        ChannelConfig configA = ChannelConfig.builder().name("A").historical(false).build();
        ChannelConfig configB = ChannelConfig.builder().name("B").historical(true).build();
        validator.validate(configA, configB, false);
    }

    @Test(expected = InvalidRequestException.class)
    public void testHistoricalNotMax() throws Exception {
        ChannelConfig configA = ChannelConfig.builder().name("A").historical(true).maxItems(10).build();
        validator.validate(configA, null, false);
    }

    @Test
    public void testChangeStorageLoss() throws Exception {
        HubProperties.setProperty("hub.protect.channels", "true");
        ChannelConfig single = ChannelConfig.builder().name("storage").storage(ChannelConfig.SINGLE).build();
        ChannelConfig batch = ChannelConfig.builder().name("storage").storage(ChannelConfig.BATCH).build();
        ChannelConfig both = ChannelConfig.builder().name("storage").storage(ChannelConfig.BOTH).build();
        validator.validate(both, single, false);
        validator.validate(both, batch, false);
        validateError(single, both);
        validateError(batch, both);
        validateError(single, batch);
        validateError(batch, single);
    }

    @Test
    public void testRemoveTagsLoss() throws Exception {
        HubProperties.setProperty("hub.protect.channels", "true");
        ChannelConfig oneTwo = ChannelConfig.builder().name("testRemoveTags").tags(Arrays.asList("one", "two")).build();
        ChannelConfig oneThree = ChannelConfig.builder().name("testRemoveTags").tags(Arrays.asList("one", "three")).build();
        ChannelConfig oneTwoThree = ChannelConfig.builder().name("testRemoveTags").tags(Arrays.asList("one", "two", "three")).build();
        validator.validate(oneTwo, oneTwo, false);
        validator.validate(oneTwoThree, oneTwo, false);
        validateError(oneTwo, oneThree);
    }

    @Test
    public void testTtlDaysLoss() throws Exception {
        HubProperties.setProperty("hub.protect.channels", "true");
        ChannelConfig ten = ChannelConfig.builder().name("testTtlDays").ttlDays(10).build();
        ChannelConfig eleven = ChannelConfig.builder().name("testTtlDays").ttlDays(11).build();
        validator.validate(ten, ten, false);
        validateError(ten, eleven);
    }

    @Test
    public void testMaxItemsLoss() throws Exception {
        HubProperties.setProperty("hub.protect.channels", "true");
        ChannelConfig ten = ChannelConfig.builder().name("testMaxItems").maxItems(10).build();
        ChannelConfig eleven = ChannelConfig.builder().name("testMaxItems").maxItems(11).build();
        validator.validate(ten, ten, false);
        validateError(ten, eleven);
    }

    @Test
    public void testReplicationLoss() throws Exception {
        HubProperties.setProperty("hub.protect.channels", "true");
        ChannelConfig changed = ChannelConfig.builder().name("testReplication").replicationSource("http://hub/channel/name1").build();
        ChannelConfig replication = ChannelConfig.builder().name("testReplication").replicationSource("http://hub/channel/name").build();
        validator.validate(changed, changed, false);
        validator.validate(replication, replication, false);
        validateError(changed, replication);
    }

    @Test
    public void testGlobalLossSatellite() throws Exception {
        HubProperties.setProperty("hub.protect.channels", "true");
        GlobalConfig twoSatellites = getGlobalConfig();
        twoSatellites.addSatellite("http://satellite2");
        ChannelConfig two = ChannelConfig.builder().name("testGlobalLoss").global(twoSatellites).build();
        GlobalConfig globalConfigOld = getGlobalConfig();
        ChannelConfig one = ChannelConfig.builder().name("testGlobalLoss").global(globalConfigOld).build();
        ChannelConfig none = ChannelConfig.builder().name("testGlobalLoss").global(null).build();
        validator.validate(two, one, false);
        validateError(one, two);
        validateError(none, one);
    }

    @Test
    public void testGlobalLossMaster() throws Exception {
        HubProperties.setProperty("hub.protect.channels", "true");
        GlobalConfig globalConfigOld = getGlobalConfig();
        ChannelConfig master = ChannelConfig.builder().name("testGlobalLoss").global(globalConfigOld).build();
        ChannelConfig none = ChannelConfig.builder().name("testGlobalLoss").global(null).build();
        GlobalConfig globalConfig = getGlobalConfig();
        globalConfig.setMaster("http://master2");
        ChannelConfig other = ChannelConfig.builder().name("testGlobalLoss").global(globalConfig).build();
        validator.validate(master, none, false);
        validateError(none, master);
        validateError(other, master);
    }

    @Test
    public void testDataLossChange() throws Exception {
        HubProperties.setProperty("hub.protect.channels", "false");
        ChannelConfig dataLoss = ChannelConfig.builder().name("testDataLossChange").protect(false).build();
        ChannelConfig noLoss = ChannelConfig.builder().name("testDataLossChange").protect(true).build();

        validateError(dataLoss, noLoss);
        validator.validate(noLoss, dataLoss, false);
        validator.validate(dataLoss, noLoss, true);

    }
    private GlobalConfig getGlobalConfig() {
        GlobalConfig twoSatellites = new GlobalConfig();
        twoSatellites.setMaster("http://master");
        twoSatellites.addSatellite("http://satellite");
        return twoSatellites;
    }


    private void validateError(ChannelConfig config, ChannelConfig oldConfig) {
        try {
            validator.validate(config, oldConfig, false);
            fail("expected exception");
        } catch (ForbiddenRequestException e) {
            //this is expected
        }
    }


}
