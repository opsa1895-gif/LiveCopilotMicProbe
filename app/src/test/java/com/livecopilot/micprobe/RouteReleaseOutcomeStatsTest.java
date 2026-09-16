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
    public void clearResetsAllReasonBuckets() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-speedup", "stable");
        stats.record("file-speedup", "reversal");
        stats.clear();

        assertTrue(stats.isEmpty());
        assertEquals(0, stats.count("rt-speedup", "stable"));
        assertEquals(0, stats.count("file-speedup", "reversal"));
    }
}
