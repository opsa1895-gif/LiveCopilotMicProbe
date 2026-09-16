package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteReleaseTransitionReliabilityCancelReasonTest {
    @Test
    public void cancellationClassifierDistinguishesRevertAndSupersede() {
        assertEquals("reverted", RouteReleaseOutcomeStats.transitionReliabilityCancellationReason(
                "strong", "mixed", "strong"));
        assertEquals("superseded", RouteReleaseOutcomeStats.transitionReliabilityCancellationReason(
                "strong", "mixed", "weak"));
        assertEquals("-", RouteReleaseOutcomeStats.transitionReliabilityCancellationReason(
                "strong", "mixed", "mixed"));
        assertEquals("-", RouteReleaseOutcomeStats.transitionReliabilityCancellationReason(
                "strong", null, "mixed"));
    }

    @Test
    public void returnToLatchedReliabilityCountsAsRevertedCancellation() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("file-slowdown");

        add(stats, "file-slowdown", "ssssr");
        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("file-slowdown"));
        add(stats, "file-slowdown", "sr");

        assertEquals(1, stats.canceledTransitionReliabilityChangeCount("file-slowdown"));
        assertEquals(1, stats.revertedTransitionReliabilityChangeCount("file-slowdown"));
        assertEquals(0, stats.supersededTransitionReliabilityChangeCount("file-slowdown"));
        assertTrue(stats.diagnostics().contains("rtr=0/1 rcancel=1/0"));
    }

    @Test
    public void confirmedTransitionDoesNotPolluteCancellationReasons() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-speedup");

        add(stats, "rt-speedup", "ssssr");
        add(stats, "rt-speedup", "rrsr");

        assertEquals(1, stats.confirmedTransitionReliabilityChangeCount("rt-speedup"));
        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("rt-speedup"));
        assertEquals(0, stats.revertedTransitionReliabilityChangeCount("rt-speedup"));
        assertEquals(0, stats.supersededTransitionReliabilityChangeCount("rt-speedup"));
        assertEquals(0, stats.revertedTransitionReliabilityChangeCount("unknown"));
        assertEquals(0, stats.supersededTransitionReliabilityChangeCount("unknown"));
    }

    @Test
    public void clearResetsReliabilityCancellationBreakdown() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("file-speedup");
        add(stats, "file-speedup", "ssssr");
        add(stats, "file-speedup", "sr");
        assertEquals(1, stats.revertedTransitionReliabilityChangeCount("file-speedup"));

        stats.clear();

        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("file-speedup"));
        assertEquals(0, stats.revertedTransitionReliabilityChangeCount("file-speedup"));
        assertEquals(0, stats.supersededTransitionReliabilityChangeCount("file-speedup"));
        assertEquals("", stats.diagnostics());
    }

    private static RouteReleaseOutcomeStats stronglyConfirmed(String reason) {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        add(stats, reason, "sssrrsrrr");
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
