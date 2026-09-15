package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingObservabilityPolicyTest {
    @Test
    public void borderlineAdvantageReportsSwitchGuard() {
        long now = 100_000L;
        long realtimeSampleAt = 99_000L;
        long fileSampleAt = 99_500L;

        assertEquals(700L, RealtimeRoutingPolicy.routeLatencyRiskGapMs(
                2_400L, 100L, 1_700L, 100L));
        assertEquals(1_000L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                100L, 4, realtimeSampleAt,
                100L, 4, fileSampleAt, now));
        assertEquals(1_000L, RealtimeRoutingPolicy.routeLatencySwitchGuardRemainingMs(
                100L, 4, realtimeSampleAt,
                100L, 4, fileSampleAt, now));
        assertEquals("switch-guard", RealtimeRoutingPolicy.routeLatencyDecisionReason(
                2_400L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
    }

    @Test
    public void strongAdvantageReportsFileFaster() {
        long now = 100_000L;
        long realtimeSampleAt = 99_000L;
        long fileSampleAt = 99_500L;

        assertEquals(1_300L, RealtimeRoutingPolicy.routeLatencyRiskGapMs(
                3_000L, 100L, 1_700L, 100L));
        assertEquals("file-faster", RealtimeRoutingPolicy.routeLatencyDecisionReason(
                3_000L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
        assertEquals(11_000L, RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                3_000L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                3_000L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
    }

    @Test
    public void elapsedProbeWindowReportsRealtimeProbe() {
        long now = 100_000L;
        long realtimeSampleAt = 80_000L;
        long fileSampleAt = 99_500L;

        assertEquals(0L, RealtimeRoutingPolicy.routeLatencySwitchGuardRemainingMs(
                100L, 4, realtimeSampleAt,
                100L, 4, fileSampleAt, now));
        assertEquals("rt-probe", RealtimeRoutingPolicy.routeLatencyDecisionReason(
                3_000L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                3_000L, 100L, 4, realtimeSampleAt,
                1_700L, 100L, 4, fileSampleAt, now));
    }

    @Test
    public void staleFileEvidenceReportsStaleReason() {
        long now = 100_000L;

        assertEquals("file-stale", RealtimeRoutingPolicy.routeLatencyDecisionReason(
                3_000L, 100L, 4, 99_000L,
                1_700L, 100L, 4, 79_999L, now));
        assertEquals(0L, RealtimeRoutingPolicy.routeLatencySwitchGuardRemainingMs(
                100L, 4, 99_000L,
                100L, 4, 79_999L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                3_000L, 100L, 4, 99_000L,
                1_700L, 100L, 4, 79_999L, now));
    }

    @Test
    public void insufficientSamplesReportLearning() {
        long now = 100_000L;

        assertEquals("learning", RealtimeRoutingPolicy.routeLatencyDecisionReason(
                2_400L, 100L, 2, 99_000L,
                1_700L, 100L, 2, 99_500L, now));
        assertEquals(0L, RealtimeRoutingPolicy.routeLatencySwitchGuardRemainingMs(
                100L, 2, 99_000L,
                100L, 2, 99_500L, now));
    }
}
