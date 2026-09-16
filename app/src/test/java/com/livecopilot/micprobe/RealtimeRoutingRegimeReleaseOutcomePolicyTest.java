package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RealtimeRoutingRegimeReleaseOutcomePolicyTest {
    private static final long RELEASE_AT = 100_000L;

    @Test
    public void releaseReasonMapsToExpectedTargetRoute() {
        assertEquals(RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.routeFlapReleaseTargetRoute("rt-slowdown"));
        assertEquals(RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.routeFlapReleaseTargetRoute("file-speedup"));
        assertEquals(RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RealtimeRoutingPolicy.routeFlapReleaseTargetRoute("rt-speedup"));
        assertEquals(RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RealtimeRoutingPolicy.routeFlapReleaseTargetRoute("file-slowdown"));
        assertEquals(RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RealtimeRoutingPolicy.routeFlapReleaseTargetRoute("-"));
    }

    @Test
    public void targetRouteMustHoldForThreeTrackableTurnsToBecomeStable() {
        int target = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE;
        assertEquals("pending", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                target, RELEASE_AT, 0, target, RELEASE_AT + 1_000L));
        int streak = RealtimeRoutingPolicy.nextRouteFlapReleaseStableStreak(
                0, target, RELEASE_AT, target, RELEASE_AT + 1_000L);
        assertEquals(1, streak);
        assertEquals("pending", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                target, RELEASE_AT, streak, target, RELEASE_AT + 2_000L));
        streak = RealtimeRoutingPolicy.nextRouteFlapReleaseStableStreak(
                streak, target, RELEASE_AT, target, RELEASE_AT + 2_000L);
        assertEquals(2, streak);
        assertEquals("stable", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                target, RELEASE_AT, streak, target, RELEASE_AT + 3_000L));
    }

    @Test
    public void oppositeTrackableRouteIsQuickReversal() {
        assertEquals("reversal", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RELEASE_AT, 1, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT + 2_000L));
        assertEquals(0, RealtimeRoutingPolicy.nextRouteFlapReleaseStableStreak(
                1, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RELEASE_AT, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT + 2_000L));
    }

    @Test
    public void staleObservationExpiresInsteadOfClassifyingRoute() {
        assertEquals("expired", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT, 2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT + RealtimeRoutingPolicy.ROUTE_FLAP_WINDOW_MS + 1L));
        assertEquals(0, RealtimeRoutingPolicy.nextRouteFlapReleaseStableStreak(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT + RealtimeRoutingPolicy.ROUTE_FLAP_WINDOW_MS + 1L));
    }

    @Test
    public void unknownReleaseTargetDoesNotCreateOutcome() {
        assertEquals("-", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RELEASE_AT, 0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RELEASE_AT + 1_000L));
    }
}
