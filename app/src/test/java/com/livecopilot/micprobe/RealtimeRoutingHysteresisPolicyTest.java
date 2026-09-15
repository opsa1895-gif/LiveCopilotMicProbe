package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingHysteresisPolicyTest {
    @Test
    public void newerBorderlineFileEvidenceGetsShortSwitchGuard() {
        long now = 100_000L;
        long realtimeSampleAt = 99_000L;
        long fileSampleAt = 99_500L;

        assertEquals(1_000L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                100L, 4, realtimeSampleAt,
                100L, 4, fileSampleAt, now));
        assertTrue(RealtimeRoutingPolicy.hasConfidentFileLatencyAdvantage(
                2_400L, 100L, 4,
                1_700L, 100L, 4));
        assertFalse(RealtimeRoutingPolicy.hasGuardedFileLatencyAdvantage(
                2_400L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
    }

    @Test
    public void borderlineAdvantageMaySwitchAfterGuardExpires() {
        long realtimeSampleAt = 99_000L;
        long fileSampleAt = 99_500L;
        long now = 101_000L;

        assertEquals(700L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                100L, 4, realtimeSampleAt,
                100L, 4, fileSampleAt, now));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
        assertEquals(4_000L, RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                2_400L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
    }

    @Test
    public void strongAdvantageCanBypassShortGuard() {
        long now = 100_000L;
        long realtimeSampleAt = 99_000L;
        long fileSampleAt = 99_500L;

        assertTrue(RealtimeRoutingPolicy.hasGuardedFileLatencyAdvantage(
                3_000L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                3_000L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
        assertEquals(11_000L, RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                3_000L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
    }

    @Test
    public void sameAgeEvidencePreservesExistingConfidenceBoundary() {
        long now = 100_000L;
        long sampleAt = 99_000L;

        assertEquals(700L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                100L, 3, sampleAt,
                100L, 3, sampleAt, now));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 3, sampleAt,
                1_700L, 100L, 3, sampleAt, now));
    }

    @Test
    public void noisyConfidenceQualifiedGapStillGetsDebounced() {
        long now = 100_000L;
        long realtimeSampleAt = 99_000L;
        long fileSampleAt = 99_500L;

        assertEquals(1_600L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                900L, 4, realtimeSampleAt,
                900L, 4, fileSampleAt, now));
        assertTrue(RealtimeRoutingPolicy.hasConfidentFileLatencyAdvantage(
                5_000L, 900L, 4,
                3_500L, 900L, 4));
        assertFalse(RealtimeRoutingPolicy.hasGuardedFileLatencyAdvantage(
                5_000L, 900L, 4, realtimeSampleAt,
                3_500L, 900L, 4, fileSampleAt, now));
    }

    @Test
    public void staleFileEvidenceNeverWinsThroughGuard() {
        long now = 100_000L;
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                5_000L, 100L, 4, 99_000L,
                1_000L, 100L, 4, 79_999L, now));
    }
}
