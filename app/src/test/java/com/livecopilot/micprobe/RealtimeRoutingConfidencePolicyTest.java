package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingConfidencePolicyTest {
    @Test
    public void stableEvidenceKeepsExistingLatencyMarginNearlyUnchanged() {
        assertEquals(750L, RealtimeRoutingPolicy.routeLatencyPreferenceMarginMs(
                100L, 3, 100L, 3));
        assertEquals(700L, RealtimeRoutingPolicy.routeLatencyPreferenceMarginMs(
                100L, 4, 100L, 4));

        assertTrue(RealtimeRoutingPolicy.hasConfidentFileLatencyAdvantage(
                2_500L, 100L, 3,
                1_700L, 100L, 3));
    }

    @Test
    public void minimumSampleConfidenceRejectsBorderlineAdvantage() {
        assertFalse(RealtimeRoutingPolicy.hasConfidentFileLatencyAdvantage(
                2_420L, 100L, 3,
                1_700L, 100L, 3));
        assertTrue(RealtimeRoutingPolicy.hasConfidentFileLatencyAdvantage(
                2_420L, 100L, 4,
                1_700L, 100L, 4));
    }

    @Test
    public void temporaryHighJitterRequiresAmeaningfullyLargerGap() {
        assertEquals(1_300L, RealtimeRoutingPolicy.routeLatencyPreferenceMarginMs(
                900L, 4, 900L, 4));
        assertFalse(RealtimeRoutingPolicy.hasConfidentFileLatencyAdvantage(
                3_500L, 900L, 4,
                2_700L, 900L, 4));
        assertTrue(RealtimeRoutingPolicy.hasConfidentFileLatencyAdvantage(
                5_000L, 900L, 4,
                2_700L, 900L, 4));
    }

    @Test
    public void confidenceMarginIsBoundedUnderExtremeJitter() {
        assertEquals(1_900L, RealtimeRoutingPolicy.routeLatencyPreferenceMarginMs(
                5_000L, 8, 5_000L, 8));
        assertEquals(Long.MAX_VALUE, RealtimeRoutingPolicy.routeLatencyPreferenceMarginMs(
                -1L, 4, 100L, 4));
        assertEquals(Long.MAX_VALUE, RealtimeRoutingPolicy.routeLatencyPreferenceMarginMs(
                100L, 2, 100L, 4));
    }

    @Test
    public void probeHoldOnlyStartsWhenFileAdvantageIsConfident() {
        long now = 100_000L;
        long realtimeSampleAt = 99_000L;
        long fileSampleAt = 99_500L;

        assertEquals(0L, RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                3_500L, 900L, 4, realtimeSampleAt,
                2_700L, 900L, 4, fileSampleAt, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                3_500L, 900L, 4, realtimeSampleAt,
                2_700L, 900L, 4, fileSampleAt, now));

        assertEquals(5_000L, RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                5_000L, 900L, 4, realtimeSampleAt,
                2_700L, 900L, 4, fileSampleAt, now));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                5_000L, 900L, 4, realtimeSampleAt,
                2_700L, 900L, 4, fileSampleAt, now));
    }
}
