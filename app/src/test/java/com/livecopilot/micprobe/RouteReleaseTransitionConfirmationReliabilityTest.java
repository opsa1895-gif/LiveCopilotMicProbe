package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteReleaseTransitionConfirmationReliabilityTest {
    @Test
    public void classifierUsesLearnStrongMixedAndWeakBoundaries() {
        assertEquals("learn",
                RouteReleaseOutcomeStats.transitionConfirmationReliabilityLabelForRate(1, 100));
        assertEquals("strong",
                RouteReleaseOutcomeStats.transitionConfirmationReliabilityLabelForRate(2, 75));
        assertEquals("mixed",
                RouteReleaseOutcomeStats.transitionConfirmationReliabilityLabelForRate(2, 74));
        assertEquals("mixed",
                RouteReleaseOutcomeStats.transitionConfirmationReliabilityLabelForRate(2, 26));
        assertEquals("weak",
                RouteReleaseOutcomeStats.transitionConfirmationReliabilityLabelForRate(2, 25));
    }

    @Test
    public void unresolvedAndUnknownReasonsDoNotPretendToBeReliable() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();

        assertEquals("-", stats.transitionConfirmationReliabilityLabel("unknown"));
        assertEquals("learn", stats.transitionConfirmationReliabilityLabel("rt-slowdown"));

        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("rt-slowdown", "reversal");
        }
        for (int i = 0; i < 6; i++) {
            stats.record("rt-slowdown", "stable");
        }

        assertEquals(1, stats.supersededSignalTransitionCount("rt-slowdown"));
        assertEquals(0, stats.transitionConfirmationRateSampleCount("rt-slowdown"));
        assertEquals("learn", stats.transitionConfirmationReliabilityLabel("rt-slowdown"));
    }

    @Test
    public void twoConfirmedTransitionsProduceStrongRecentReliability() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        String[] outcomes = {
                "stable", "stable", "stable",
                "reversal", "reversal", "stable",
                "reversal", "reversal", "reversal"
        };
        for (String outcome : outcomes) {
            stats.record("rt-speedup", outcome);
        }

        assertEquals(2, stats.confirmedSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-speedup"));
        assertEquals(2, stats.transitionConfirmationRateSampleCount("rt-speedup"));
        assertEquals(100, stats.transitionConfirmationRatePercent("rt-speedup"));
        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("rt-speedup"));
        assertTrue(stats.diagnostics().contains("conf100%@2/strong"));
    }

    @Test
    public void balancedConfirmedAndRevertedHistoryIsMixed() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        String[] outcomes = {
                "stable", "stable", "reversal", "stable", "stable",
                "stable", "reversal", "stable", "reversal", "reversal"
        };
        for (String outcome : outcomes) {
            stats.record("file-speedup", outcome);
        }

        assertEquals(1, stats.confirmedSignalTransitionCount("file-speedup"));
        assertEquals(1, stats.revertedSignalTransitionCount("file-speedup"));
        assertEquals(50, stats.transitionConfirmationRatePercent("file-speedup"));
        assertEquals("mixed", stats.transitionConfirmationReliabilityLabel("file-speedup"));
        assertTrue(stats.diagnostics().contains("conf50%@2/mixed"));
    }

    @Test
    public void recentWindowCanAgeIntoWeakReliabilityAndClearReturnsToLearn() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        String[] cycle = {"stable", "stable", "stable", "reversal", "reversal", "stable"};
        for (int repeat = 0; repeat < 10; repeat++) {
            for (String outcome : cycle) {
                stats.record("file-slowdown", outcome);
            }
        }

        assertEquals(RouteReleaseOutcomeStats.TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES,
                stats.transitionConfirmationRateSampleCount("file-slowdown"));
        assertEquals(0, stats.transitionConfirmationRatePercent("file-slowdown"));
        assertEquals("weak", stats.transitionConfirmationReliabilityLabel("file-slowdown"));
        assertTrue(stats.diagnostics().contains("conf0%@8/weak"));

        stats.clear();

        assertEquals(0, stats.transitionConfirmationRateSampleCount("file-slowdown"));
        assertEquals(-1, stats.transitionConfirmationRatePercent("file-slowdown"));
        assertEquals("learn", stats.transitionConfirmationReliabilityLabel("file-slowdown"));
    }
}
