package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RouteReleaseTransitionReliabilityConfirmationRateTest {
    @Test
    public void rateRequiresTwoResolvedConfirmationOrReversionOutcomes() {
        assertEquals(0, RouteReleaseOutcomeStats
                .reliabilityTransitionConfirmationRateSampleCountForCounts(0, 0));
        assertEquals(1, RouteReleaseOutcomeStats
                .reliabilityTransitionConfirmationRateSampleCountForCounts(1, 0));
        assertEquals(-1, RouteReleaseOutcomeStats
                .reliabilityTransitionConfirmationRatePercentForCounts(1, 0));
        assertEquals(-1, RouteReleaseOutcomeStats
                .reliabilityTransitionConfirmationRatePercentForCounts(0, 1));
        assertEquals(50, RouteReleaseOutcomeStats
                .reliabilityTransitionConfirmationRatePercentForCounts(1, 1));
        assertEquals(100, RouteReleaseOutcomeStats
                .reliabilityTransitionConfirmationRatePercentForCounts(2, 0));
        assertEquals(0, RouteReleaseOutcomeStats
                .reliabilityTransitionConfirmationRatePercentForCounts(0, 2));
    }

    @Test
    public void supersededIsExcludedFromRateDenominatorByConstruction() {
        assertEquals(4, RouteReleaseOutcomeStats
                .reliabilityTransitionConfirmationRateSampleCountForCounts(3, 1));
        assertEquals(75, RouteReleaseOutcomeStats
                .reliabilityTransitionConfirmationRatePercentForCounts(3, 1));
    }

    @Test
    public void realReversionFeedsRateAndClearResetsIt() {
        RouteReleaseOutcomeStats stats = stronglyConfirmed("file-speedup");
        add(stats, "file-speedup", "ssssr");
        add(stats, "file-speedup", "sr");

        assertEquals(1, stats.revertedTransitionReliabilityChangeCount("file-speedup"));
        assertEquals(1, stats.reliabilityTransitionConfirmationRateSampleCount("file-speedup"));
        assertEquals(-1, stats.reliabilityTransitionConfirmationRatePercent("file-speedup"));
        assertEquals(0, stats.reliabilityTransitionConfirmationRateSampleCount("unknown"));
        assertEquals(-1, stats.reliabilityTransitionConfirmationRatePercent("unknown"));

        stats.clear();

        assertEquals(0, stats.reliabilityTransitionConfirmationRateSampleCount("file-speedup"));
        assertEquals(-1, stats.reliabilityTransitionConfirmationRatePercent("file-speedup"));
    }

    @Test
    public void negativeSyntheticCountsAreClampedForHelperSafety() {
        assertEquals(2, RouteReleaseOutcomeStats
                .reliabilityTransitionConfirmationRateSampleCountForCounts(-3, 2));
        assertEquals(0, RouteReleaseOutcomeStats
                .reliabilityTransitionConfirmationRatePercentForCounts(-3, 2));
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
