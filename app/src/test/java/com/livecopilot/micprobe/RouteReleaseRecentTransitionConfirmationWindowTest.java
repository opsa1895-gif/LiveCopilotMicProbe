package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteReleaseRecentTransitionConfirmationWindowTest {
    @Test
    public void confirmationRateUsesOnlyMostRecentEightResolvedTransitions() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        String[] cycle = {"stable", "stable", "stable", "reversal", "reversal", "stable"};

        for (int repeat = 0; repeat < 10; repeat++) {
            for (String outcome : cycle) {
                stats.record("rt-slowdown", outcome);
            }
        }

        assertEquals(2, stats.confirmedSignalTransitionCount("rt-slowdown"));
        assertEquals(8, stats.revertedSignalTransitionCount("rt-slowdown"));
        assertEquals(18, stats.supersededSignalTransitionCount("rt-slowdown"));
        assertEquals(RouteReleaseOutcomeStats.TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES,
                stats.transitionConfirmationRateSampleCount("rt-slowdown"));
        assertEquals(0, stats.transitionConfirmationRatePercent("rt-slowdown"));
        assertTrue(stats.diagnostics().contains("conf0%@8"));
    }

    @Test
    public void recentConfirmationWindowsStayIndependentAcrossReleaseReasons() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        String[] agingCycle = {"stable", "stable", "stable", "reversal", "reversal", "stable"};
        for (int repeat = 0; repeat < 10; repeat++) {
            for (String outcome : agingCycle) {
                stats.record("rt-slowdown", outcome);
            }
        }

        String[] balanced = {
                "stable", "stable", "reversal", "stable", "stable",
                "stable", "reversal", "stable", "reversal", "reversal"
        };
        for (String outcome : balanced) {
            stats.record("file-speedup", outcome);
        }

        assertEquals(8, stats.transitionConfirmationRateSampleCount("rt-slowdown"));
        assertEquals(0, stats.transitionConfirmationRatePercent("rt-slowdown"));
        assertEquals(2, stats.transitionConfirmationRateSampleCount("file-speedup"));
        assertEquals(50, stats.transitionConfirmationRatePercent("file-speedup"));
    }

    @Test
    public void supersededTransitionsStillDoNotConsumeRecentConfirmationWindow() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("file-slowdown", "reversal");
        }
        for (int i = 0; i < 6; i++) {
            stats.record("file-slowdown", "stable");
        }

        assertEquals(1, stats.supersededSignalTransitionCount("file-slowdown"));
        assertEquals(0, stats.transitionConfirmationRateSampleCount("file-slowdown"));
        assertEquals(-1, stats.transitionConfirmationRatePercent("file-slowdown"));
    }

    @Test
    public void clearResetsRecentConfirmationWindowWithoutChangingCumulativeSemanticsBeforeReset() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        String[] outcomes = {
                "stable", "stable", "reversal", "stable", "stable",
                "stable", "reversal", "stable", "reversal", "reversal"
        };
        for (String outcome : outcomes) {
            stats.record("rt-speedup", outcome);
        }

        assertEquals(1, stats.confirmedSignalTransitionCount("rt-speedup"));
        assertEquals(1, stats.revertedSignalTransitionCount("rt-speedup"));
        assertEquals(2, stats.transitionConfirmationRateSampleCount("rt-speedup"));
        assertEquals(50, stats.transitionConfirmationRatePercent("rt-speedup"));

        stats.clear();

        assertEquals(0, stats.confirmedSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.transitionConfirmationRateSampleCount("rt-speedup"));
        assertEquals(-1, stats.transitionConfirmationRatePercent("rt-speedup"));
    }
}
