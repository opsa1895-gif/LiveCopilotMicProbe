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
    }

    @Test
    public void diagnosticsShowOnlyReasonsSeenInThisSession() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "stable");
        stats.record("rt-slowdown", "reversal");
        stats.record("file-slowdown", "expired");

        assertEquals("rel s/r/x rtslow 1/1/0 fslow 0/0/1", stats.diagnostics());
    }

    @Test
    public void reversalRateWaitsForThreeDirectionalOutcomes() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "reversal");
        stats.record("rt-slowdown", "reversal");

        assertEquals(2, stats.reversalRateSampleCount("rt-slowdown"));
        assertEquals(-1, stats.reversalRatePercent("rt-slowdown"));
        assertEquals("rel s/r/x rtslow 0/2/0", stats.diagnostics());

        stats.record("rt-slowdown", "stable");

        assertEquals(RouteReleaseOutcomeStats.MIN_REVERSAL_RATE_SAMPLES,
                stats.reversalRateSampleCount("rt-slowdown"));
        assertEquals(66, stats.reversalRatePercent("rt-slowdown"));
        assertEquals("rel s/r/x rtslow 1/2/0 rev66%@3", stats.diagnostics());
    }

    @Test
    public void expiredOutcomesDoNotDiluteDirectionalReversalRate() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("file-speedup", "stable");
        stats.record("file-speedup", "reversal");
        stats.record("file-speedup", "reversal");
        stats.record("file-speedup", "expired");
        stats.record("file-speedup", "expired");

        assertEquals(3, stats.reversalRateSampleCount("file-speedup"));
        assertEquals(66, stats.reversalRatePercent("file-speedup"));
        assertEquals("rel s/r/x ffast 1/2/2 rev66%@3", stats.diagnostics());
    }

    @Test
    public void reversalRatesStayIndependentAcrossReleaseReasons() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (int i = 0; i < 3; i++) stats.record("rt-speedup", "stable");
        for (int i = 0; i < 3; i++) stats.record("file-slowdown", "reversal");

        assertEquals(0, stats.reversalRatePercent("rt-speedup"));
        assertEquals(100, stats.reversalRatePercent("file-slowdown"));
        assertEquals(
                "rel s/r/x rtfast 3/0/0 rev0%@3 fslow 0/3/0 rev100%@3",
                stats.diagnostics());
    }

    @Test
    public void clearResetsAllReasonBucketsAndRates() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "stable");
        stats.record("rt-speedup", "reversal");
        stats.record("file-speedup", "reversal");
        stats.clear();

        assertTrue(stats.isEmpty());
        assertEquals(0, stats.count("rt-speedup", "stable"));
        assertEquals(0, stats.count("file-speedup", "reversal"));
        assertEquals(0, stats.reversalRateSampleCount("rt-speedup"));
        assertEquals(-1, stats.reversalRatePercent("rt-speedup"));
    }
}
