package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingRouteReversalMemoryPolicyTest {
    @Test
    public void singleOneWaySwitchDoesNotRaiseFlapScore() {
        assertEquals(0, RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 1_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L));
        assertFalse(RealtimeRoutingPolicy.isRouteFlapReversal(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME));
    }

    @Test
    public void quickReturnToPreviousRouteConfirmsFlapping() {
        assertTrue(RealtimeRoutingPolicy.isRouteFlapReversal(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE));
        assertEquals(1, RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 3_000L));
    }

    @Test
    public void continuedAlternationRaisesScoreToCap() {
        int score = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 3_000L);
        score = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                score, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 3_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 4_000L);
        score = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                score, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 4_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 5_000L);
        assertEquals(RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE, score);
    }

    @Test
    public void stableChoiceDecaysAndStaleHistoryCannotConfirmReversal() {
        assertEquals(2, RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 3_000L));
        assertEquals(0, RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 1_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 21_001L));
    }

    @Test
    public void diagnosticsExposeTwoRouteHistoryWithoutInventingUnknownRoute() {
        assertEquals("file>rt", RealtimeRoutingPolicy.performanceRouteHistoryLabel(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME));
        assertEquals("file", RealtimeRoutingPolicy.performanceRouteHistoryLabel(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE));
        assertEquals("-", RealtimeRoutingPolicy.performanceRouteHistoryLabel(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN));
    }
}
