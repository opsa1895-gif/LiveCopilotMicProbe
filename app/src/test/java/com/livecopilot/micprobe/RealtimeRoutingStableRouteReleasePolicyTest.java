package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingStableRouteReleasePolicyTest {
    @Test
    public void stableRouteStreakConfirmsAfterThreeTurns() {
        int streak = RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(
                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE);
        assertEquals(1, streak);
        streak = RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(
                streak, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE);
        assertEquals(2, streak);
        streak = RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(
                streak, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE);
        assertEquals(RealtimeRoutingPolicy.ROUTE_FLAP_STABLE_CONFIRM_TURNS, streak);
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistory(streak));
    }

    @Test
    public void switchingRoutesRestartsStableStreak() {
        assertEquals(1, RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(
                RealtimeRoutingPolicy.ROUTE_FLAP_STABLE_CONFIRM_TURNS,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME));
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistory(2));
    }

    @Test
    public void stableRunReleasesOldReversalTarget() {
        int score = 2;
        int streak = 1;
        int previous = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE;
        int current = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME;

        score = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                score, previous, current, 1_000L, current, 2_000L);
        streak = RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(streak, current, current);
        assertEquals(1, score);
        assertEquals(2, streak);

        score = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                score, previous, current, 2_000L, current, 3_000L);
        streak = RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(streak, current, current);
        assertEquals(0, score);
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistory(streak));

        previous = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
        assertEquals(0, RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                score, previous, current, 3_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 4_000L));
    }

    @Test
    public void idleHistoryIsExplicitlyStale() {
        assertTrue(RealtimeRoutingPolicy.isRouteFlapHistoryFresh(1_000L, 21_000L));
        assertFalse(RealtimeRoutingPolicy.isRouteFlapHistoryFresh(1_000L, 21_001L));
        assertFalse(RealtimeRoutingPolicy.isRouteFlapHistoryFresh(2_000L, 1_999L));
        assertEquals(0, RealtimeRoutingPolicy.activeRouteFlapScore(
                RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE, 1_000L, 21_001L));
    }
}
