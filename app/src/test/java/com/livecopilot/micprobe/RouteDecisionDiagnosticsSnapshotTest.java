package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
