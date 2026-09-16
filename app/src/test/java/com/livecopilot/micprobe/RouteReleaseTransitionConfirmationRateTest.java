package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteReleaseTransitionConfirmationRateTest {
    @Test
    public void unknownOrUnresolvedTransitionsHaveNoConfirmationRate() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();

        assertEquals(0, stats.transitionConfirmationRateSampleCount("unknown"));
        assertEquals(-1, stats.transitionConfirmationRatePercent("unknown"));

        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("rt-slowdown", "reversal");
        }
        for (int i = 0; i < 6; i++) {
            stats.record("rt-slowdown", "stable");
        }

        assertEquals(0, stats.confirmedSignalTransitionCount("rt-slowdown"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-slowdown"));
        assertEquals(1, stats.supersededSignalTransitionCount("rt-slowdown"));
        assertEquals(0, stats.transitionConfirmationRateSampleCount("rt-slowdown"));
        assertEquals(-1, stats.transitionConfirmationRatePercent("rt-slowdown"));
        assertFalse(stats.diagnostics().contains(" conf"));
    }

    @Test
    public void singleResolvedTransitionIsQueryableButHiddenFromLowSampleDiagnostics() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "reversal");
        stats.record("rt-speedup", "reversal");
        stats.record("rt-speedup", "stable");

        assertEquals(1, stats.confirmedSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-speedup"));
        assertEquals(1, stats.transitionConfirmationRateSampleCount("rt-speedup"));
        assertEquals(100, stats.transitionConfirmationRatePercent("rt-speedup"));
        assertEquals(RouteReleaseOutcomeStats.MIN_TRANSITION_CONFIRMATION_RATE_SAMPLES, 2);
        assertFalse(stats.diagnostics().contains(" conf"));
    }

    @Test
    public void confirmationRateUsesConfirmedAndRevertedButExcludesSuperseded() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        String[] outcomes = {
                "stable", "stable", "reversal", "stable", "stable",
                "stable", "reversal", "stable", "reversal", "reversal"
        };
        for (String outcome : outcomes) {
            stats.record("rt-slowdown", outcome);
        }

        assertEquals(1, stats.confirmedSignalTransitionCount("rt-slowdown"));
        assertEquals(2, stats.canceledSignalTransitionCount("rt-slowdown"));
        assertEquals(1, stats.revertedSignalTransitionCount("rt-slowdown"));
        assertEquals(1, stats.supersededSignalTransitionCount("rt-slowdown"));
        assertEquals(2, stats.transitionConfirmationRateSampleCount("rt-slowdown"));
        assertEquals(50, stats.transitionConfirmationRatePercent("rt-slowdown"));
        assertEquals(
                "rel s/r/x rtslow 6/4/0 rev50%@8 sig=stable>risk×1/2 tr=1/2 cancel=1/1 conf50%@2/mixed",
                stats.diagnostics());
    }

    @Test
    public void clearResetsConfirmationRateObservability() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        String[] outcomes = {
                "stable", "stable", "reversal", "stable", "stable",
                "stable", "reversal", "stable", "reversal", "reversal"
        };
        for (String outcome : outcomes) {
            stats.record("file-slowdown", outcome);
        }
        assertTrue(stats.transitionConfirmationRateSampleCount("file-slowdown") > 0);

        stats.clear();

        assertEquals(0, stats.transitionConfirmationRateSampleCount("file-slowdown"));
        assertEquals(-1, stats.transitionConfirmationRatePercent("file-slowdown"));
        assertEquals("", stats.diagnostics());
    }
}
