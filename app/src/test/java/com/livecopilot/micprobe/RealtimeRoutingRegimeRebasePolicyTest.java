package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingRegimeRebasePolicyTest {
    @Test
    public void firstOutlierStillUsesRobustWinsorizedLearning() {
        long estimate = 1_600L;
        long jitter = 200L;
        int samples = 3;
        int direction = RealtimeRoutingPolicy.routeLatencyOutlierDirection(
                estimate, samples, 8_000L);
        int streak = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                0, 0, direction, true);

        assertEquals(1, direction);
        assertEquals(1, streak);
        assertEquals(2_350L, RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                estimate, samples, 8_000L, direction, streak));
        assertEquals(900L, RealtimeRoutingPolicy.nextRouteLatencyJitter(
                jitter, estimate, samples, 8_000L, direction, streak));
    }

    @Test
    public void confirmedUpwardRegimeRebasesEstimateWithoutTreatingWholeShiftAsJitter() {
        long estimate = 1_600L;
        long jitter = 200L;
        int samples = 3;

        int firstDirection = RealtimeRoutingPolicy.routeLatencyOutlierDirection(
                estimate, samples, 8_000L);
        int firstStreak = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                0, 0, firstDirection, true);
        long firstJitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                jitter, estimate, samples, 8_000L, firstDirection, firstStreak);
        long firstEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                estimate, samples, 8_000L, firstDirection, firstStreak);

        int secondDirection = RealtimeRoutingPolicy.routeLatencyOutlierDirection(
                firstEstimate, samples + 1, 8_000L);
        int secondStreak = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                firstDirection, firstStreak, secondDirection, true);
        long rebasedEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                firstEstimate, samples + 1, 8_000L, secondDirection, secondStreak);
        long rebasedJitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                firstJitter, firstEstimate, samples + 1, 8_000L,
                secondDirection, secondStreak);
        long ordinaryEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                firstEstimate, samples + 1, 8_000L);

        assertEquals(2, secondStreak);
        assertEquals(5_175L, rebasedEstimate);
        assertEquals(1_381L, rebasedJitter);
        assertEquals(3_100L, ordinaryEstimate);
        assertTrue(rebasedEstimate > ordinaryEstimate);
        assertEquals(6_556L, RealtimeRoutingPolicy.riskAdjustedRouteLatency(
                rebasedEstimate, rebasedJitter));
    }

    @Test
    public void confirmedDownwardRegimeRebasesRecoveryFasterThanOrdinaryWinsorization() {
        long estimate = 8_000L;
        long jitter = 300L;
        int samples = 5;

        int firstDirection = RealtimeRoutingPolicy.routeLatencyOutlierDirection(
                estimate, samples, 1_000L);
        int firstStreak = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                0, 0, firstDirection, true);
        long firstJitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                jitter, estimate, samples, 1_000L, firstDirection, firstStreak);
        long firstEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                estimate, samples, 1_000L, firstDirection, firstStreak);

        int secondDirection = RealtimeRoutingPolicy.routeLatencyOutlierDirection(
                firstEstimate, samples + 1, 1_000L);
        int secondStreak = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                firstDirection, firstStreak, secondDirection, true);
        long rebasedEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                firstEstimate, samples + 1, 1_000L, secondDirection, secondStreak);
        long rebasedJitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                firstJitter, firstEstimate, samples + 1, 1_000L,
                secondDirection, secondStreak);
        long ordinaryEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                firstEstimate, samples + 1, 1_000L);

        assertEquals(-1, firstDirection);
        assertEquals(7_000L, firstEstimate);
        assertEquals(1_225L, firstJitter);
        assertEquals(2, secondStreak);
        assertEquals(4_000L, rebasedEstimate);
        assertEquals(1_669L, rebasedJitter);
        assertEquals(6_000L, ordinaryEstimate);
        assertTrue(rebasedEstimate < ordinaryEstimate);
    }

    @Test
    public void confirmedRegimeRebaseStillHonorsAbsoluteSampleCap() {
        assertEquals(10_000L, RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                8_000L, 5, Long.MAX_VALUE, 1, 2));
        assertEquals(4_000L, RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                8_000L, 5, 0L, -1, 2));
    }
}
