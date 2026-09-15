package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingAdaptiveHysteresisPolicyTest {
    @Test
    public void matureStableEvidenceShortensSwitchGuard() {
        assertEquals(1_500L, RealtimeRoutingPolicy.adaptiveRouteLatencySwitchGuardMs(
                100L, 6, 100L, 6));
        assertEquals(200L, RealtimeRoutingPolicy.adaptiveRouteLatencySwitchExtraMarginMs(
                100L, 6, 100L, 6));

        long realtimeSampleAt = 99_000L;
        long fileSampleAt = 99_500L;
        long now = 100_600L;
        assertEquals(700L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                100L, 6, realtimeSampleAt,
                100L, 6, fileSampleAt, now));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 6, realtimeSampleAt,
                1_700L, 100L, 6, fileSampleAt, now));
    }

    @Test
    public void moderateJitterExtendsGuardWithoutChangingMargin() {
        assertEquals(2_500L, RealtimeRoutingPolicy.adaptiveRouteLatencySwitchGuardMs(
                400L, 4, 400L, 4));
        assertEquals(300L, RealtimeRoutingPolicy.adaptiveRouteLatencySwitchExtraMarginMs(
                400L, 4, 400L, 4));
    }

    @Test
    public void highJitterKeepsBorderlineSwitchDebouncedLonger() {
        assertEquals(3_500L, RealtimeRoutingPolicy.adaptiveRouteLatencySwitchGuardMs(
                900L, 4, 900L, 4));

        long realtimeSampleAt = 99_000L;
        long fileSampleAt = 99_500L;
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                5_000L, 900L, 4, realtimeSampleAt,
                3_700L, 900L, 4, fileSampleAt, 101_500L));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                5_000L, 900L, 4, realtimeSampleAt,
                3_700L, 900L, 4, fileSampleAt, 102_500L));
    }

    @Test
    public void minimallySampledNoisyEvidenceGetsMaximumProtection() {
        assertEquals(4_000L, RealtimeRoutingPolicy.adaptiveRouteLatencySwitchGuardMs(
                900L, 3, 900L, 3));
        assertEquals(500L, RealtimeRoutingPolicy.adaptiveRouteLatencySwitchExtraMarginMs(
                900L, 3, 900L, 3));

        long realtimeSampleAt = 99_000L;
        long fileSampleAt = 99_500L;
        assertEquals(1_900L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                900L, 3, realtimeSampleAt,
                900L, 3, fileSampleAt, 100_000L));
        assertFalse(RealtimeRoutingPolicy.hasGuardedFileLatencyAdvantage(
                5_000L, 900L, 3, realtimeSampleAt,
                3_300L, 900L, 3, fileSampleAt, 100_000L));
    }

    @Test
    public void legacyStableFourSampleBoundaryIsPreserved() {
        assertEquals(2_000L, RealtimeRoutingPolicy.adaptiveRouteLatencySwitchGuardMs(
                100L, 4, 100L, 4));
        assertEquals(300L, RealtimeRoutingPolicy.adaptiveRouteLatencySwitchExtraMarginMs(
                100L, 4, 100L, 4));
        assertEquals(1_000L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                100L, 4, 99_000L,
                100L, 4, 99_500L, 100_000L));
    }
}
