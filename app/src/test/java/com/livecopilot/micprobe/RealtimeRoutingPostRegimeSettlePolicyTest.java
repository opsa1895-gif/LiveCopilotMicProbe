package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingPostRegimeSettlePolicyTest {
    @Test
    public void confirmedRegimeStartsTwoFreshSettlingSamples() {
        int settle = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                1, 2, 0, true);
        assertEquals(2, settle);
        assertTrue(RealtimeRoutingPolicy.isRouteLatencySettling(0, settle));

        settle = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                0, settle, 0, true);
        assertEquals(1, settle);
        assertTrue(RealtimeRoutingPolicy.isRouteLatencySettling(0, settle));

        settle = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                0, settle, 0, true);
        assertEquals(0, settle);
        assertFalse(RealtimeRoutingPolicy.isRouteLatencySettling(0, settle));
    }

    @Test
    public void staleEvidenceDoesNotCarrySettlingWindowAcrossIdleGap() {
        assertEquals(0, RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                1, 2, 0, false));
        assertEquals(0, RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                0, 2, 0, false));
    }

    @Test
    public void upwardRegimeSettlesQuicklyForTwoFollowingInliers() {
        long estimate = 5_175L;
        long jitter = 1_381L;
        int samples = 5;
        int direction = 0;
        int settle = 2;

        long nextEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                estimate, samples, 8_000L, direction, settle);
        long nextJitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                jitter, estimate, samples, 8_000L, direction, settle);
        assertEquals(6_588L, nextEstimate);
        assertEquals(1_389L, nextJitter);

        settle = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                direction, settle, 0, true);
        estimate = nextEstimate;
        jitter = nextJitter;
        samples++;

        nextEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                estimate, samples, 8_000L, 0, settle);
        nextJitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                jitter, estimate, samples, 8_000L, 0, settle);
        assertEquals(7_294L, nextEstimate);
        assertEquals(1_218L, nextJitter);

        settle = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                0, settle, 0, true);
        assertEquals(0, settle);
        assertEquals(7_471L, RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                nextEstimate, samples + 1, 8_000L, 0, settle));
    }

    @Test
    public void downwardRegimeSettlesSymmetricallyWithoutOvershoot() {
        long estimate = 4_000L;
        long jitter = 1_669L;
        int samples = 7;
        int settle = 2;

        long nextEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                estimate, samples, 1_000L, 0, settle);
        long nextJitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                jitter, estimate, samples, 1_000L, 0, settle);
        assertEquals(2_500L, nextEstimate);
        assertEquals(1_627L, nextJitter);

        settle = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                0, settle, 0, true);
        nextEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                nextEstimate, samples + 1, 1_000L, 0, settle);
        assertEquals(1_750L, nextEstimate);
        assertTrue(nextEstimate > 1_000L);
    }

    @Test
    public void oppositeOutlierCancelsSettlingAndUsesRobustBoundedLearning() {
        int nextDirection = RealtimeRoutingPolicy.routeLatencyOutlierDirection(
                5_175L, 5, 1_000L);
        int nextStreak = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                0, 2, nextDirection, true);

        assertEquals(-1, nextDirection);
        assertEquals(1, nextStreak);
        assertFalse(RealtimeRoutingPolicy.isRouteLatencySettling(
                nextDirection, nextStreak));
        assertEquals(4_175L, RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                5_175L, 5, 1_000L, nextDirection, nextStreak));
    }
}