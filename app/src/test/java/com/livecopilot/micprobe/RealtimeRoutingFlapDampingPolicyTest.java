package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingFlapDampingPolicyTest {
    @Test
    public void alternatingPerformanceRoutesRaiseFlapScore() {
        int score = RealtimeRoutingPolicy.nextRouteFlapScore(
                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN, 0L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 1_000L);
        assertEquals(0, score);

        score = RealtimeRoutingPolicy.nextRouteFlapScore(
                score, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 1_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L);
        assertEquals(1, score);
        score = RealtimeRoutingPolicy.nextRouteFlapScore(
                score, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 3_000L);
        assertEquals(2, score);
        score = RealtimeRoutingPolicy.nextRouteFlapScore(
                score, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 3_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 4_000L);
        assertEquals(RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE, score);
    }

    @Test
    public void stableRouteDecaysScoreAndIdleClearsIt() {
        assertEquals(2, RealtimeRoutingPolicy.nextRouteFlapScore(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 1_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 2_000L));
        assertEquals(0, RealtimeRoutingPolicy.activeRouteFlapScore(
                3, 1_000L, 22_000L));
    }

    @Test
    public void flapScoreRaisesFileSwitchMargin() {
        long realtimeSampleAt = 95_000L;
        long fileSampleAt = 99_500L;
        long now = 100_000L;

        assertEquals(700L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                100L, 4, realtimeSampleAt,
                100L, 4, fileSampleAt, now, 0));
        assertEquals(1_100L, RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                100L, 4, realtimeSampleAt,
                100L, 4, fileSampleAt, now, 2));
    }

    @Test
    public void borderlineFileAdvantageIsDampedAfterARecentFlip() {
        long realtimeSampleAt = 95_000L;
        long fileSampleAt = 99_500L;
        long now = 100_000L;

        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 4, realtimeSampleAt,
                1_600L, 100L, 4, fileSampleAt, now, 0));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 4, realtimeSampleAt,
                1_600L, 100L, 4, fileSampleAt, now, 1));
        assertEquals("flap-damp", RealtimeRoutingPolicy.routeLatencyDecisionReason(
                2_400L, 100L, 4, realtimeSampleAt,
                1_600L, 100L, 4, fileSampleAt, now, 1));
    }

    @Test
    public void strongFileAdvantageStillWinsAtMaximumDamping() {
        long realtimeSampleAt = 95_000L;
        long fileSampleAt = 99_500L;
        long now = 100_000L;

        assertEquals(600L, RealtimeRoutingPolicy.routeFlapExtraMarginMs(
                RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                3_400L, 100L, 4, realtimeSampleAt,
                1_600L, 100L, 4, fileSampleAt, now,
                RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE));
        assertEquals("file-faster", RealtimeRoutingPolicy.routeLatencyDecisionReason(
                3_400L, 100L, 4, realtimeSampleAt,
                1_600L, 100L, 4, fileSampleAt, now,
                RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE));
    }

    @Test
    public void intentionalProbeAndLearningDoNotCountAsPerformanceSwitches() {
        assertFalse(RealtimeRoutingPolicy.isTrackableRouteDecisionReason("rt-probe"));
        assertFalse(RealtimeRoutingPolicy.isTrackableRouteDecisionReason("learning"));
        assertTrue(RealtimeRoutingPolicy.isTrackableRouteDecisionReason("file-faster"));
        assertTrue(RealtimeRoutingPolicy.isTrackableRouteDecisionReason("flap-damp"));
    }
}
