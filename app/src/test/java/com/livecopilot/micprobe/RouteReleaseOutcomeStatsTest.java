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
    public void pendingAndUnknownSignalsDoNotPolluteCompletedOutcomeCounts() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "pending");
        stats.record("unknown", "stable");
        stats.record("file-speedup", "unknown");

        assertTrue(stats.isEmpty());
        assertEquals("", stats.diagnostics());
        assertEquals("-", stats.reversalSignalLabel("unknown"));
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
        assertEquals("rel s/r/x rtslow 0/2/0 sig=learn", stats.diagnostics());

        stats.record("rt-slowdown", "stable");

        assertEquals(RouteReleaseOutcomeStats.MIN_REVERSAL_RATE_SAMPLES,
                stats.reversalRateSampleCount("rt-slowdown"));
        assertEquals(66, stats.reversalRatePercent("rt-slowdown"));
        assertEquals("risk", stats.reversalSignalLabel("rt-slowdown"));
        assertEquals("rel s/r/x rtslow 1/2/0 rev66%@3 sig=risk", stats.diagnostics());
    }

    @Test
    public void signalLabelsSeparateStableMixedAndHighReversalRisk() {
        RouteReleaseOutcomeStats stable = new RouteReleaseOutcomeStats();
        stable.record("rt-speedup", "reversal");
        stable.record("rt-speedup", "stable");
        stable.record("rt-speedup", "stable");
        stable.record("rt-speedup", "stable");

        assertEquals(25, stable.reversalRatePercent("rt-speedup"));
        assertEquals("stable", stable.reversalSignalLabel("rt-speedup"));

        RouteReleaseOutcomeStats mixed = new RouteReleaseOutcomeStats();
        mixed.record("file-speedup", "reversal");
        mixed.record("file-speedup", "reversal");
        mixed.record("file-speedup", "stable");
        mixed.record("file-speedup", "stable");
        mixed.record("file-speedup", "stable");

        assertEquals(40, mixed.reversalRatePercent("file-speedup"));
        assertEquals("mixed", mixed.reversalSignalLabel("file-speedup"));

        RouteReleaseOutcomeStats risk = new RouteReleaseOutcomeStats();
        risk.record("file-slowdown", "reversal");
        risk.record("file-slowdown", "reversal");
        risk.record("file-slowdown", "stable");
        risk.record("file-slowdown", "stable");

        assertEquals(50, risk.reversalRatePercent("file-slowdown"));
        assertEquals("risk", risk.reversalSignalLabel("file-slowdown"));
    }

    @Test
    public void expiredOutcomesDoNotEnterRecentDirectionalWindow() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("file-speedup", "stable");
        stats.record("file-speedup", "reversal");
        stats.record("file-speedup", "reversal");
        stats.record("file-speedup", "expired");
        stats.record("file-speedup", "expired");

        assertEquals(3, stats.reversalRateSampleCount("file-speedup"));
        assertEquals(66, stats.reversalRatePercent("file-speedup"));
        assertEquals("risk", stats.reversalSignalLabel("file-speedup"));
        assertEquals("rel s/r/x ffast 1/2/2 rev66%@3 sig=risk", stats.diagnostics());
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
        assertEquals("rel s/r/x rtslow 8/8/0 rev0%@8 sig=stable", stats.diagnostics());
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
        assertEquals("rel s/r/x fslow 1/8/0 rev87%@8 sig=risk", stats.diagnostics());
    }

    @Test
    public void recentWindowCanMoveSignalFromRiskToStable() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("rt-speedup", "reversal");
        }
        assertEquals("risk", stats.reversalSignalLabel("rt-speedup"));

        for (int i = 0; i < 6; i++) {
            stats.record("rt-speedup", "stable");
        }

        assertEquals(25, stats.reversalRatePercent("rt-speedup"));
        assertEquals("stable", stats.reversalSignalLabel("rt-speedup"));
    }

    @Test
    public void reversalRatesStayIndependentAcrossReleaseReasons() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("rt-speedup", "stable");
            stats.record("file-slowdown", "reversal");
        }

        assertEquals(0, stats.reversalRatePercent("rt-speedup"));
        assertEquals(100, stats.reversalRatePercent("file-slowdown"));
        assertEquals("stable", stats.reversalSignalLabel("rt-speedup"));
        assertEquals("risk", stats.reversalSignalLabel("file-slowdown"));
        assertEquals(
                "rel s/r/x rtfast 8/0/0 rev0%@8 sig=stable fslow 0/8/0 rev100%@8 sig=risk",
                stats.diagnostics());
    }

    @Test
    public void clearResetsAllReasonBucketsRecentWindowsAndSignals() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {
            stats.record("rt-speedup", "reversal");
        }
        stats.record("file-speedup", "expired");
        stats.clear();

        assertTrue(stats.isEmpty());
        assertEquals(0, stats.count("rt-speedup", "reversal"));
        assertEquals(0, stats.count("file-speedup", "expired"));
        assertEquals(0, stats.reversalRateSampleCount("rt-speedup"));
        assertEquals(-1, stats.reversalRatePercent("rt-speedup"));
        assertEquals("learn", stats.reversalSignalLabel("rt-speedup"));
    }
}
