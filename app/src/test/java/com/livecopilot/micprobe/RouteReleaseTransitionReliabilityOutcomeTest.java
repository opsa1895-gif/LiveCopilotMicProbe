package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteReleaseTransitionReliabilityOutcomeTest {
    @Test
    public void initialReliabilityLatchIsNotCountedAsTransition() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-slowdown");

        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("rt-slowdown"));
        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("rt-slowdown"));
        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("unknown"));
        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("unknown"));
    }

    @Test
    public void secondMatchingCandidateConfirmsReliabilityTransition() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-speedup");

        add(stats, "rt-speedup", "ssssr");
        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("rt-speedup"));
        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("rt-speedup"));

        add(stats, "rt-speedup", "rrsr");

        assertEquals("mixed", stats.transitionConfirmationReliabilityLabel("rt-speedup"));
        assertEquals(1, stats.confirmedTransitionReliabilityChangeCount("rt-speedup"));
        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("rt-speedup"));
        assertTrue(stats.diagnostics().contains("rtr=1/0"));
    }

    @Test
    public void returnToLatchedReliabilityCancelsPendingTransition() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("file-speedup");

        add(stats, "file-speedup", "ssssr");
        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("file-speedup"));

        add(stats, "file-speedup", "sr");

        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("file-speedup"));
        assertEquals("-", stats.transitionConfirmationReliabilityPendingLabel("file-speedup"));
        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("file-speedup"));
        assertEquals(1, stats.canceledTransitionReliabilityChangeCount("file-speedup"));
        assertTrue(stats.diagnostics().contains("rtr=0/1"));
    }

    @Test
    public void countersAreIndependentPerReasonAndClearResetsThem() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-speedup");
        add(stats, "rt-speedup", "ssssr");
        add(stats, "rt-speedup", "rrsr");

        stronglyConfirmedInto(stats, "file-speedup");
        add(stats, "file-speedup", "ssssr");
        add(stats, "file-speedup", "sr");

        assertEquals(1, stats.confirmedTransitionReliabilityChangeCount("rt-speedup"));
        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("rt-speedup"));
        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("file-speedup"));
        assertEquals(1, stats.canceledTransitionReliabilityChangeCount("file-speedup"));

        stats.clear();

        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("rt-speedup"));
        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("rt-speedup"));
        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("file-speedup"));
        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("file-speedup"));
        assertEquals("", stats.diagnostics());
    }

    private static RouteReleaseOutcomeStats stronglyConfirmed(String reason) {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stronglyConfirmedInto(stats, reason);
        return stats;
    }

    private static void stronglyConfirmedInto(RouteReleaseOutcomeStats stats, String reason) {
        add(stats, reason, "sssrrsrrr");
        assertEquals(2, stats.transitionConfirmationRateSampleCount(reason));
        assertEquals(100, stats.transitionConfirmationRatePercent(reason));
        assertEquals("strong", stats.transitionConfirmationReliabilityLabel(reason));
    }

    private static void add(RouteReleaseOutcomeStats stats, String reason, String sequence) {
        for (int i = 0; i < sequence.length(); i++) {
            char sample = sequence.charAt(i);
            stats.record(reason, sample == 'r' ? "reversal" : "stable");
        }
    }
}
