package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsSnapshotTest {
    @Test
    public void learningSnapshotKeepsOnlyCoreRouteState() {
        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                "learning", 2, 1,
                false, "-", "-", 0, 3,
                "", "-", 0,
                0, 0L,
                false, Long.MIN_VALUE, Long.MAX_VALUE, 0L, 0L);

        assertEquals("route why=learning n=2/1", snapshot.format());
    }

    @Test
    public void activeReleaseHistoryAndLatencyAreGroupedIntoStableSections() {
        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                "switch-guard", 4, 4,
                true, "rt-slowdown", "pending", 1, 3,
                "rel s/r/x rtslow 1/1/0 sup=1 sig=learn",
                "rt>file", 2,
                2, 400L,
                true, 700L, 1_400L, 1_000L, 2_500L);

        assertEquals(
                "route why=switch-guard n=4/4"
                        + " • release=rt-slowdown:pending×1/3"
                        + " • stats{rel s/r/x rtslow 1/1/0 sup=1 sig=learn}"
                        + " • hist=rt>file stable×2/3"
                        + " • flap×2(+400ms)"
                        + " • lat gap=700ms need=1400ms guard=1000/2500ms",
                snapshot.format());
    }

    @Test
    public void longReleaseStatsAreBoundedAtTokenBoundaryWithoutHidingLaterSections() {
        String releaseBreakdown = longReleaseBreakdown();
        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                "switch-guard", 8, 8,
                false, "-", "-", 0, 3,
                releaseBreakdown,
                "rt>file", 1,
                1, 200L,
                true, 300L, 900L, 0L, 1_500L);

        String formatted = snapshot.format();
        assertEquals(
                "route why=switch-guard n=8/8"
                        + " • stats{rel s/r/x rtslow 1/2/3 sup=4 rev50%@8 sig=risky tr=5/6"
                        + " cancel=2/4 conf75%@8/strong rtr=3/2…}"
                        + " • hist=rt>file stable×1/3"
                        + " • flap×1(+200ms)"
                        + " • lat gap=300ms need=900ms guard=off/1500ms",
                formatted);
        String stats = statsContent(formatted);
        assertTrue(stats.length() <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET);
    }

    @Test
    public void totalBudgetShrinksStatsBeforeLaterRoutingSections() {
        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                "switch-guard", 8, 8,
                true, "rt-slowdown", "pending", 1, 3,
                longReleaseBreakdown(),
                "rt>file", 1,
                1, 200L,
                true, 300L, 900L, 0L, 1_500L);

        String formatted = snapshot.format();
        assertTrue(formatted.length() <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);
        assertTrue(formatted.contains(" • release=rt-slowdown:pending×1/3"));
        assertTrue(formatted.contains(" • stats{"));
        String stats = statsContent(formatted);
        assertTrue(stats.endsWith("…"));
        assertTrue(stats.length() < RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET);
        assertTrue(formatted.contains(" • hist=rt>file stable×1/3"));
        assertTrue(formatted.contains(" • flap×1(+200ms)"));
        assertTrue(formatted.contains(" • lat gap=300ms need=900ms guard=off/1500ms"));
    }

    @Test
    public void pathologicalLabelsStayWithinHardBudgetAndKeepSectionSignals() {
        String longLabel = "abcdefghijklmnopqrstuvwxyz0123456789".repeat(8);
        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                longLabel, Integer.MAX_VALUE, Integer.MAX_VALUE,
                true, longLabel, longLabel, Integer.MAX_VALUE, Integer.MAX_VALUE,
                longReleaseBreakdown() + longReleaseBreakdown(),
                longLabel, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Long.MAX_VALUE,
                true, Long.MAX_VALUE - 1L, Long.MAX_VALUE - 1L,
                Long.MAX_VALUE, Long.MAX_VALUE);

        String formatted = snapshot.format();
        assertTrue(formatted.length() <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);
        assertTrue(formatted.startsWith("route why="));
        assertTrue(formatted.contains(" • release="));
        assertTrue(formatted.contains(" • hist="));
        assertTrue(formatted.contains(" • flap×"));
        assertTrue(formatted.contains(" • lat"));
        assertTrue(formatted.contains("guard="));
        assertFalse(formatted.contains("stats{"));
    }

    @Test
    public void matureLatencySnapshotShowsOffGuardWithoutOptionalHistory() {
        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                "rt-margin", 8, 8,
                false, "rt-speedup", "stable", 3, 3,
                "", "-", 0,
                0, 0L,
                true, -200L, 700L, 0L, 1_500L);

        assertEquals(
                "route why=rt-margin n=8/8"
                        + " • lat gap=-200ms need=700ms guard=off/1500ms",
                snapshot.format());
    }

    @Test
    public void snapshotNormalizesInvalidOptionalLabelsAndNegativeCounters() {
        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                null, -1, -2,
                true, "-", null, -1, 0,
                null, null, -3,
                -2, -100L,
                true, Long.MIN_VALUE, Long.MAX_VALUE, -10L, -20L);

        assertEquals("route why=- n=0/0 • lat guard=off/0ms", snapshot.format());
    }

    private static String longReleaseBreakdown() {
        return "rel s/r/x rtslow 1/2/3 sup=4 rev50%@8 sig=risky tr=5/6 cancel=2/4"
                + " conf75%@8/strong rtr=3/2 rcancel=1/1 rmat=mature rconf75%@8"
                + " rtspeed 7/8/9 sup=10 rev25%@8 sig=stable";
    }

    private static String statsContent(String formatted) {
        int start = formatted.indexOf("stats{") + "stats{".length();
        return formatted.substring(start, formatted.indexOf('}', start));
    }
}
