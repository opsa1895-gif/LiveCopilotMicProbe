package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteReleaseOutcomeStatsTest {
    @Test
    public void tracksOutcomesIndependentlyForEveryReleaseReason() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "stable");
        stats.record("rt-slowdown", "reversal");
        stats.record("rt-speedup", "reversal");
        stats.record("file-speedup", "expired");
        stats.record("file-slowdown", "stable");

        assertEquals(1, stats.count("rt-slowdown", "stable"));
        assertEquals(1, stats.count("rt-slowdown", "reversal"));
        assertEquals(1, stats.count("rt-speedup", "reversal"));
        assertEquals(1, stats.count("file-speedup", "expired"));
        assertEquals(1, stats.count("file-slowdown", "stable"));
        assertEquals(0, stats.count("file-slowdown", "reversal"));
    }

    @Test
    public void supersededReleaseIsCountedButExcludedFromDirectionalRates() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "stable");
        stats.record("rt-slowdown", "reversal");
        stats.record("rt-slowdown", "superseded");
        stats.record("rt-slowdown", "superseded");

        assertEquals(2, stats.count("rt-slowdown", "superseded"));
        assertEquals(2, stats.reversalRateSampleCount("rt-slowdown"));
        assertEquals(-1, stats.reversalRatePercent("rt-slowdown"));
        assertEquals("learn", stats.reversalSignalLabel("rt-slowdown"));
        assertEquals(
                "rel s/r/x rtslow 1/1/0 sup=2 sig=learn",
                stats.diagnostics());

        stats.clear();
        assertEquals(0, stats.count("rt-slowdown", "superseded"));
        assertTrue(stats.isEmpty());
    }

    @Test
    public void pendingAndUnknownSignalsDoNotPolluteCompletedOutcomeCounts() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "pending");
        stats.record("unknown", "stable");
        stats.record("file-speedup", "unknown");

        assertTrue(stats.isEmpty());
        assertEquals("", stats.diagnostics());
        assertEquals("-", stats.reversalSignalLabel("unknown"));
        assertEquals("-", stats.reversalSignalPendingLabel("unknown"));
        assertEquals(0, stats.reversalSignalPendingStreak("unknown"));
        assertEquals(0, stats.confirmedSignalTransitionCount("unknown"));
        assertEquals(0, stats.canceledSignalTransitionCount("unknown"));
        assertEquals(0, stats.revertedSignalTransitionCount("unknown"));
        assertEquals(0, stats.supersededSignalTransitionCount("unknown"));
    }

    @Test
    public void diagnosticsShowOnlyReasonsSeenInThisSession() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "stable");
        stats.record("rt-slowdown", "reversal");
        stats.record("file-slowdown", "expired");

        assertEquals(
                "rel s/r/x rtslow 1/1/0 sig=learn fslow 0/0/1 sig=learn",
                stats.diagnostics());
    }

    @Test
    public void reversalRateWaitsForThreeDirectionalOutcomes() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "reversal");
        stats.record("rt-slowdown", "reversal");

        assertEquals(2, stats.reversalRateSampleCount("rt-slowdown"));
        assertEquals(-1, stats.reversalRatePercent("rt-slowdown"));
        assertEquals("learn", stats.reversalSignalLabel("rt-slowdown"));
        assertEquals("-", stats.reversalSignalPendingLabel("rt-slowdown"));
        assertEquals(0, stats.reversalSignalPendingStreak("rt-slowdown"));
        assertEquals("rel s/r/x rtslow 0/2/0 sig=learn", stats.diagnostics());

        stats.record("rt-slowdown", "stable");

        assertEquals(RouteReleaseOutcomeStats.MIN_REVERSAL_RATE_SAMPLES,
                stats.reversalRateSampleCount("rt-slowdown"));
        assertEquals(66, stats.reversalRatePercent("rt-slowdown"));
        assertEquals("risk", stats.reversalSignalLabel("rt-slowdown"));
        assertEquals("-", stats.reversalSignalPendingLabel("rt-slowdown"));
        assertEquals(0, stats.reversalSignalPendingStreak("rt-slowdown"));
        assertEquals("rel s/r/x rtslow 1/2/0 rev66%@3 sig=risk", stats.diagnostics());
    }

    @Test
    public void signalLabelsSeparateStableMixedAndHighReversalRisk() {
        RouteReleaseOutcomeStats stable = new RouteReleaseOutcomeStats();
        stable.record("rt-speedup", "stable");
        stable.record("rt-speedup", "stable");
        stable.record("rt-speedup", "stable");
        stable.record("rt-speedup", "reversal");

        assertEquals(25, stable.reversalRatePercent("rt-speedup"));
        assertEquals("stable", stable.reversalSignalLabel("rt-speedup"));

        RouteReleaseOutcomeStats mixed = new RouteReleaseOutcomeStats();
        mixed.record("file-speedup", "reversal");
        mixed.record("file-speedup", "stable");
        mixed.record("file-speedup", "stable");

        assertEquals(33, mixed.reversalRatePercent("file-speedup"));
        assertEquals("mixed", mixed.reversalSignalLabel("file-speedup"));

        RouteReleaseOutcomeStats risk = new RouteReleaseOutcomeStats();
        risk.record("file-slowdown", "reversal");
        risk.record("file-slowdown", "reversal");
        risk.record("file-slowdown", "stable");

        assertEquals(66, risk.reversalRatePercent("file-slowdown"));
        assertEquals("risk", risk.reversalSignalLabel("file-slowdown"));
    }

    @Test
    public void singleBoundaryClassificationShowsPendingTransitionWithoutFlippingLabel() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "reversal");
        stats.record("rt-speedup", "reversal");

        assertEquals(40, stats.reversalRatePercent("rt-speedup"));
        assertEquals("stable", stats.reversalSignalLabel("rt-speedup"));
        assertEquals("mixed", stats.reversalSignalPendingLabel("rt-speedup"));
        assertEquals(1, stats.reversalSignalPendingStreak("rt-speedup"));
        assertEquals(0, stats.confirmedSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.canceledSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.supersededSignalTransitionCount("rt-speedup"));
        assertEquals(
                "rel s/r/x rtfast 3/2/0 rev40%@5 sig=stable>mixed×1/2",
                stats.diagnostics());
    }

    @Test
    public void secondConsecutiveCandidateClassificationCommitsAndCountsConfirmation() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "reversal");
        stats.record("rt-speedup", "reversal");
        stats.record("rt-speedup", "stable");

        assertEquals(RouteReleaseOutcomeStats.SIGNAL_CHANGE_CONFIRM_OUTCOMES, 2);
        assertEquals(33, stats.reversalRatePercent("rt-speedup"));
        assertEquals("mixed", stats.reversalSignalLabel("rt-speedup"));
        assertEquals("-", stats.reversalSignalPendingLabel("rt-speedup"));
        assertEquals(0, stats.reversalSignalPendingStreak("rt-speedup"));
        assertEquals(1, stats.confirmedSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.canceledSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.supersededSignalTransitionCount("rt-speedup"));
        assertEquals(
                "rel s/r/x rtfast 4/2/0 rev33%@6 sig=mixed tr=1/0",
                stats.diagnostics());
    }

    @Test
    public void returningToLatchedSignalCountsRevertedCancellation() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("file-speedup", "reversal");
        stats.record("file-speedup", "stable");
        stats.record("file-speedup", "stable");
        assertEquals("mixed", stats.reversalSignalLabel("file-speedup"));

        stats.record("file-speedup", "reversal");
        assertEquals("risk", stats.reversalSignalPendingLabel("file-speedup"));
        assertEquals(0, stats.canceledSignalTransitionCount("file-speedup"));

        stats.record("file-speedup", "stable");
        assertEquals("-", stats.reversalSignalPendingLabel("file-speedup"));
        assertEquals(0, stats.confirmedSignalTransitionCount("file-speedup"));
        assertEquals(1, stats.canceledSignalTransitionCount("file-speedup"));
        assertEquals(1, stats.revertedSignalTransitionCount("file-speedup"));
        assertEquals(0, stats.supersededSignalTransitionCount("file-speedup"));
        assertEquals(
                "rel s/r/x ffast 3/2/0 rev40%@5 sig=mixed tr=0/1 cancel=1/0",
                stats.diagnostics());
    }

    @Test
    public void candidateReplacementCountsSupersededCancellationBeforeNewCandidateCanConfirm() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("rt-slowdown", "reversal");
        }
        assertEquals("risk", stats.reversalSignalLabel("rt-slowdown"));

        for (int i = 0; i < 5; i++) {
            stats.record("rt-slowdown", "stable");
        }
        assertEquals("mixed", stats.reversalSignalPendingLabel("rt-slowdown"));
        assertEquals(0, stats.canceledSignalTransitionCount("rt-slowdown"));

        stats.record("rt-slowdown", "stable");
        assertEquals("stable", stats.reversalSignalPendingLabel("rt-slowdown"));
        assertEquals(1, stats.canceledSignalTransitionCount("rt-slowdown"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-slowdown"));
        assertEquals(1, stats.supersededSignalTransitionCount("rt-slowdown"));
        assertEquals(0, stats.confirmedSignalTransitionCount("rt-slowdown"));

        stats.record("rt-slowdown", "stable");
        assertEquals("stable", stats.reversalSignalLabel("rt-slowdown"));
        assertEquals(1, stats.canceledSignalTransitionCount("rt-slowdown"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-slowdown"));
        assertEquals(1, stats.supersededSignalTransitionCount("rt-slowdown"));
        assertEquals(1, stats.confirmedSignalTransitionCount("rt-slowdown"));
    }

    @Test
    public void expiredOutcomesDoNotEnterWindowOrResolvePendingTransition() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("file-speedup", "stable");
        stats.record("file-speedup", "stable");
        stats.record("file-speedup", "stable");
        stats.record("file-speedup", "reversal");
        stats.record("file-speedup", "reversal");

        assertEquals(5, stats.reversalRateSampleCount("file-speedup"));
        assertEquals(40, stats.reversalRatePercent("file-speedup"));
        assertEquals("stable", stats.reversalSignalLabel("file-speedup"));
        assertEquals("mixed", stats.reversalSignalPendingLabel("file-speedup"));
        assertEquals(1, stats.reversalSignalPendingStreak("file-speedup"));

        stats.record("file-speedup", "expired");
        stats.record("file-speedup", "expired");

        assertEquals(5, stats.reversalRateSampleCount("file-speedup"));
        assertEquals(40, stats.reversalRatePercent("file-speedup"));
        assertEquals("stable", stats.reversalSignalLabel("file-speedup"));
        assertEquals("mixed", stats.reversalSignalPendingLabel("file-speedup"));
        assertEquals(1, stats.reversalSignalPendingStreak("file-speedup"));
        assertEquals(0, stats.confirmedSignalTransitionCount("file-speedup"));
        assertEquals(0, stats.canceledSignalTransitionCount("file-speedup"));
        assertEquals(0, stats.revertedSignalTransitionCount("file-speedup"));
        assertEquals(0, stats.supersededSignalTransitionCount("file-speedup"));
        assertEquals(
                "rel s/r/x ffast 3/2/2 rev40%@5 sig=stable>mixed×1/2",
                stats.diagnostics());

        stats.record("file-speedup", "stable");
        assertEquals(33, stats.reversalRatePercent("file-speedup"));
        assertEquals("mixed", stats.reversalSignalLabel("file-speedup"));
        assertEquals("-", stats.reversalSignalPendingLabel("file-speedup"));
        assertEquals(1, stats.confirmedSignalTransitionCount("file-speedup"));
    }

    @Test
    public void reversalRateUsesOnlyMostRecentEightDirectionalOutcomes() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("rt-slowdown", "reversal");
        }

        assertEquals(RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES,
                stats.reversalRateSampleCount("rt-slowdown"));
        assertEquals(100, stats.reversalRatePercent("rt-slowdown"));
        assertEquals("risk", stats.reversalSignalLabel("rt-slowdown"));

        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("rt-slowdown", "stable");
        }

        assertEquals(RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES,
                stats.reversalRateSampleCount("rt-slowdown"));
        assertEquals(0, stats.reversalRatePercent("rt-slowdown"));
        assertEquals("stable", stats.reversalSignalLabel("rt-slowdown"));
        assertEquals("-", stats.reversalSignalPendingLabel("rt-slowdown"));
        assertEquals(1, stats.confirmedSignalTransitionCount("rt-slowdown"));
        assertEquals(1, stats.canceledSignalTransitionCount("rt-slowdown"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-slowdown"));
        assertEquals(1, stats.supersededSignalTransitionCount("rt-slowdown"));
        assertEquals(
                "rel s/r/x rtslow 8/8/0 rev0%@8 sig=stable tr=1/1 cancel=0/1",
                stats.diagnostics());
    }

    @Test
    public void slidingWindowDropsOldestDirectionalOutcomeOneAtATime() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("file-slowdown", "reversal");
        }
        stats.record("file-slowdown", "stable");

        assertEquals(RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES,
                stats.reversalRateSampleCount("file-slowdown"));
        assertEquals(87, stats.reversalRatePercent("file-slowdown"));
        assertEquals("risk", stats.reversalSignalLabel("file-slowdown"));
        assertEquals("-", stats.reversalSignalPendingLabel("file-slowdown"));
        assertEquals("rel s/r/x fslow 1/8/0 rev87%@8 sig=risk", stats.diagnostics());
    }

    @Test
    public void recentWindowShowsSupersededCandidateBeforeStableTransitionConfirms() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("rt-speedup", "reversal");
        }
        assertEquals("risk", stats.reversalSignalLabel("rt-speedup"));

        for (int i = 0; i < 6; i++) {
            stats.record("rt-speedup", "stable");
        }
        assertEquals(25, stats.reversalRatePercent("rt-speedup"));
        assertEquals("risk", stats.reversalSignalLabel("rt-speedup"));
        assertEquals("stable", stats.reversalSignalPendingLabel("rt-speedup"));
        assertEquals(1, stats.reversalSignalPendingStreak("rt-speedup"));
        assertEquals(1, stats.canceledSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-speedup"));
        assertEquals(1, stats.supersededSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.confirmedSignalTransitionCount("rt-speedup"));

        stats.record("rt-speedup", "stable");
        assertEquals(12, stats.reversalRatePercent("rt-speedup"));
        assertEquals("stable", stats.reversalSignalLabel("rt-speedup"));
        assertEquals("-", stats.reversalSignalPendingLabel("rt-speedup"));
        assertEquals(0, stats.reversalSignalPendingStreak("rt-speedup"));
        assertEquals(1, stats.canceledSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-speedup"));
        assertEquals(1, stats.supersededSignalTransitionCount("rt-speedup"));
        assertEquals(1, stats.confirmedSignalTransitionCount("rt-speedup"));
    }

    @Test
    public void cancellationReasonsStayIndependentAcrossReleaseReasons() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();

        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "reversal");
        stats.record("rt-speedup", "reversal");
        stats.record("rt-speedup", "stable");

        stats.record("file-speedup", "reversal");
        stats.record("file-speedup", "stable");
        stats.record("file-speedup", "stable");
        stats.record("file-speedup", "reversal");
        stats.record("file-speedup", "stable");

        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("file-slowdown", "reversal");
        }
        for (int i = 0; i < 6; i++) {
            stats.record("file-slowdown", "stable");
        }

        assertEquals(1, stats.confirmedSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.canceledSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.revertedSignalTransitionCount("rt-speedup"));
        assertEquals(0, stats.supersededSignalTransitionCount("rt-speedup"));

        assertEquals(0, stats.confirmedSignalTransitionCount("file-speedup"));
        assertEquals(1, stats.canceledSignalTransitionCount("file-speedup"));
        assertEquals(1, stats.revertedSignalTransitionCount("file-speedup"));
        assertEquals(0, stats.supersededSignalTransitionCount("file-speedup"));

        assertEquals(0, stats.confirmedSignalTransitionCount("file-slowdown"));
        assertEquals(1, stats.canceledSignalTransitionCount("file-slowdown"));
        assertEquals(0, stats.revertedSignalTransitionCount("file-slowdown"));
        assertEquals(1, stats.supersededSignalTransitionCount("file-slowdown"));
    }

    @Test
    public void clearResetsAllReasonBucketsRecentWindowsAndCancellationReasons() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("file-speedup", "reversal");
        stats.record("file-speedup", "stable");
        stats.record("file-speedup", "stable");
        stats.record("file-speedup", "reversal");
        stats.record("file-speedup", "stable");
        assertEquals(1, stats.revertedSignalTransitionCount("file-speedup"));
        stats.clear();

        assertTrue(stats.isEmpty());
        assertEquals(0, stats.count("file-speedup", "reversal"));
        assertEquals(0, stats.reversalRateSampleCount("file-speedup"));
        assertEquals(-1, stats.reversalRatePercent("file-speedup"));
        assertEquals("learn", stats.reversalSignalLabel("file-speedup"));
        assertEquals("-", stats.reversalSignalPendingLabel("file-speedup"));
        assertEquals(0, stats.reversalSignalPendingStreak("file-speedup"));
        assertEquals(0, stats.confirmedSignalTransitionCount("file-speedup"));
        assertEquals(0, stats.canceledSignalTransitionCount("file-speedup"));
        assertEquals(0, stats.revertedSignalTransitionCount("file-speedup"));
        assertEquals(0, stats.supersededSignalTransitionCount("file-speedup"));
    }
}
