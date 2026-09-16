package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteReleaseTransitionConfirmationReliabilityHysteresisTest {
    @Test
    public void singleBoundaryCrossingIsHeldUntilSecondResolvedObservation() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-speedup");

        add(stats, "rt-speedup", "ssssr");

        assertEquals(3, stats.transitionConfirmationRateSampleCount("rt-speedup"));
        assertEquals(66, stats.transitionConfirmationRatePercent("rt-speedup"));
        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("rt-speedup"));
        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("rt-speedup"));
        assertEquals(1, stats.transitionConfirmationReliabilityPendingStreak("rt-speedup"));
        assertTrue(stats.diagnostics().contains("conf66%@3/strong>mixed×1/2"));

        add(stats, "rt-speedup", "rrsr");

        assertEquals(4, stats.transitionConfirmationRateSampleCount("rt-speedup"));
        assertEquals(50, stats.transitionConfirmationRatePercent("rt-speedup"));
        assertEquals("mixed", stats.transitionConfirmationReliabilityLabel("rt-speedup"));
        assertEquals("-", stats.transitionConfirmationReliabilityPendingLabel("rt-speedup"));
        assertEquals(0, stats.transitionConfirmationReliabilityPendingStreak("rt-speedup"));
        assertTrue(stats.diagnostics().contains("conf50%@4/mixed"));
        assertFalse(stats.diagnostics().contains("conf50%@4/mixed>"));
    }

    @Test
    public void returnToLatchedReliabilityCancelsPendingCandidate() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("file-speedup");
        add(stats, "file-speedup", "ssssr");
        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("file-speedup"));

        add(stats, "file-speedup", "sr");

        assertEquals(4, stats.transitionConfirmationRateSampleCount("file-speedup"));
        assertEquals(75, stats.transitionConfirmationRatePercent("file-speedup"));
        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("file-speedup"));
        assertEquals("-", stats.transitionConfirmationReliabilityPendingLabel("file-speedup"));
        assertEquals(0, stats.transitionConfirmationReliabilityPendingStreak("file-speedup"));
        assertTrue(stats.diagnostics().contains("conf75%@4/strong"));
    }

    @Test
    public void reliabilityHysteresisIsIndependentPerReason() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-slowdown");
        add(stats, "file-slowdown", "sssrrsrrr");

        add(stats, "rt-slowdown", "ssssr");

        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("rt-slowdown"));
        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("rt-slowdown"));
        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("file-slowdown"));
        assertEquals("-", stats.transitionConfirmationReliabilityPendingLabel("file-slowdown"));
    }

    @Test
    public void clearDropsLatchedAndPendingReliabilityState() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-slowdown");
        add(stats, "rt-slowdown", "ssssr");
        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("rt-slowdown"));

        stats.clear();

        assertEquals("learn", stats.transitionConfirmationReliabilityLabel("rt-slowdown"));
        assertEquals("-", stats.transitionConfirmationReliabilityPendingLabel("rt-slowdown"));
        assertEquals(0, stats.transitionConfirmationReliabilityPendingStreak("rt-slowdown"));
        assertEquals("", stats.diagnostics());
    }

    private static RouteReleaseOutcomeStats stronglyConfirmed(String reason) {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        add(stats, reason, "sssrrsrrr");
        assertEquals(2, stats.transitionConfirmationRateSampleCount(reason));
        assertEquals(100, stats.transitionConfirmationRatePercent(reason));
        assertEquals("strong", stats.transitionConfirmationReliabilityLabel(reason));
        return stats;
    }

    private static void add(RouteReleaseOutcomeStats stats, String reason, String sequence) {
        for (int i = 0; i < sequence.length(); i++) {
            char sample = sequence.charAt(i);
            stats.record(reason, sample == 'r' ? "reversal" : "stable");
        }
    }
}
