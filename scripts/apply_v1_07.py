from pathlib import Path

BUILD = Path("app/build.gradle.kts")
CLIENT = Path("app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java")
SNAPSHOT = Path("app/src/main/java/com/livecopilot/micprobe/RouteDecisionDiagnosticsSnapshot.java")
TEST = Path("app/src/test/java/com/livecopilot/micprobe/RouteDecisionDiagnosticsSnapshotTest.java")

build = BUILD.read_text()
old_version = '        versionCode = 107\n        versionName = "1.06.0-superseded-release-outcome"'
new_version = '        versionCode = 108\n        versionName = "1.07.0-compact-diagnostic-snapshot"'
if old_version not in build:
    raise SystemExit("unexpected app version")
BUILD.write_text(build.replace(old_version, new_version, 1))

client = CLIENT.read_text()
start_marker = "    synchronized String routeDecisionDiagnostics() {"
end_marker = "    synchronized void noteRealtimeTranscriptOutcome"
start = client.index(start_marker)
end = client.index(end_marker, start)
new_method = '''    synchronized String routeDecisionDiagnostics() {
        long now = System.currentTimeMillis();
        int activeFlapScore = RealtimeRoutingPolicy.activeRouteFlapScore(
                routeFlapScore, lastPerformanceRouteDecisionAtMs, now);
        boolean releaseFresh = RealtimeRoutingPolicy.isRouteFlapHistoryFresh(
                lastRouteFlapReleaseAtMs, now);
        String releaseBreakdown = routeFlapReleaseOutcomeStats.diagnostics();
        boolean routeHistoryFresh = RealtimeRoutingPolicy.isRouteFlapHistoryFresh(
                lastPerformanceRouteDecisionAtMs, now);
        String routeHistory = routeHistoryFresh
                ? RealtimeRoutingPolicy.performanceRouteHistoryLabel(
                        previousPerformanceRoute, lastPerformanceRoute)
                : "-";
        boolean latencyReady = realtimeRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES
                && fileRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES;
        long riskGapMs = Long.MIN_VALUE;
        long requiredMarginMs = Long.MAX_VALUE;
        long guardDurationMs = 0L;
        long guardRemainingMs = 0L;
        if (latencyReady) {
            riskGapMs = RealtimeRoutingPolicy.routeLatencyRiskGapMs(
                    realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                    fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs);
            requiredMarginMs = RealtimeRoutingPolicy.routeLatencySwitchMarginMs(
                    realtimeRouteLatencyJitterMs, realtimeRouteLatencySamples,
                    realtimeRouteLatencySampleAtMs, fileRouteLatencyJitterMs,
                    fileRouteLatencySamples, fileRouteLatencySampleAtMs, now, activeFlapScore);
            guardDurationMs = RealtimeRoutingPolicy.adaptiveRouteLatencySwitchGuardMs(
                    realtimeRouteLatencyJitterMs, realtimeRouteLatencySamples,
                    fileRouteLatencyJitterMs, fileRouteLatencySamples);
            guardRemainingMs = RealtimeRoutingPolicy.routeLatencySwitchGuardRemainingMs(
                    realtimeRouteLatencyJitterMs, realtimeRouteLatencySamples,
                    realtimeRouteLatencySampleAtMs, fileRouteLatencyJitterMs,
                    fileRouteLatencySamples, fileRouteLatencySampleAtMs, now);
        }
        return new RouteDecisionDiagnosticsSnapshot(
                lastRouteDecisionReason,
                realtimeRouteLatencySamples,
                fileRouteLatencySamples,
                releaseFresh,
                lastRouteFlapReleaseReason,
                lastRouteFlapReleaseOutcome,
                lastRouteFlapReleaseStableStreak,
                RealtimeRoutingPolicy.ROUTE_FLAP_STABLE_CONFIRM_TURNS,
                releaseBreakdown,
                routeHistory,
                stablePerformanceRouteStreak,
                activeFlapScore,
                RealtimeRoutingPolicy.routeFlapExtraMarginMs(activeFlapScore),
                latencyReady,
                riskGapMs,
                requiredMarginMs,
                guardRemainingMs,
                guardDurationMs).format();
    }

'''
CLIENT.write_text(client[:start] + new_method + client[end:])

SNAPSHOT.write_text('''package com.livecopilot.micprobe;

final class RouteDecisionDiagnosticsSnapshot {
    private final String decisionReason;
    private final int realtimeSamples;
    private final int fileSamples;
    private final boolean releaseFresh;
    private final String releaseReason;
    private final String releaseOutcome;
    private final int releaseStableStreak;
    private final int confirmTurns;
    private final String releaseBreakdown;
    private final String routeHistory;
    private final int stableRouteStreak;
    private final int flapScore;
    private final long flapExtraMarginMs;
    private final boolean latencyReady;
    private final long riskGapMs;
    private final long requiredMarginMs;
    private final long guardRemainingMs;
    private final long guardDurationMs;

    RouteDecisionDiagnosticsSnapshot(
            String decisionReason,
            int realtimeSamples,
            int fileSamples,
            boolean releaseFresh,
            String releaseReason,
            String releaseOutcome,
            int releaseStableStreak,
            int confirmTurns,
            String releaseBreakdown,
            String routeHistory,
            int stableRouteStreak,
            int flapScore,
            long flapExtraMarginMs,
            boolean latencyReady,
            long riskGapMs,
            long requiredMarginMs,
            long guardRemainingMs,
            long guardDurationMs) {
        this.decisionReason = decisionReason;
        this.realtimeSamples = Math.max(0, realtimeSamples);
        this.fileSamples = Math.max(0, fileSamples);
        this.releaseFresh = releaseFresh;
        this.releaseReason = releaseReason;
        this.releaseOutcome = releaseOutcome;
        this.releaseStableStreak = Math.max(0, releaseStableStreak);
        this.confirmTurns = Math.max(1, confirmTurns);
        this.releaseBreakdown = releaseBreakdown;
        this.routeHistory = routeHistory;
        this.stableRouteStreak = Math.max(0, stableRouteStreak);
        this.flapScore = Math.max(0, flapScore);
        this.flapExtraMarginMs = Math.max(0L, flapExtraMarginMs);
        this.latencyReady = latencyReady;
        this.riskGapMs = riskGapMs;
        this.requiredMarginMs = requiredMarginMs;
        this.guardRemainingMs = Math.max(0L, guardRemainingMs);
        this.guardDurationMs = Math.max(0L, guardDurationMs);
    }

    String format() {
        StringBuilder out = new StringBuilder("route why=")
                .append(labelOrDash(decisionReason))
                .append(" n=").append(realtimeSamples).append('/').append(fileSamples);
        if (releaseFresh && isKnown(releaseReason)) {
            out.append(" • release=").append(releaseReason)
                    .append(':').append(labelOrDash(releaseOutcome));
            if ("pending".equals(releaseOutcome)) {
                out.append('×').append(releaseStableStreak).append('/').append(confirmTurns);
            }
        }
        if (releaseBreakdown != null && !releaseBreakdown.isEmpty()) {
            out.append(" • stats{").append(releaseBreakdown).append('}');
        }
        if (isKnown(routeHistory)) {
            out.append(" • hist=").append(routeHistory);
            if (stableRouteStreak > 0) {
                out.append(" stable×").append(stableRouteStreak).append('/').append(confirmTurns);
            }
        }
        if (flapScore > 0) {
            out.append(" • flap×").append(flapScore)
                    .append("(+").append(flapExtraMarginMs).append("ms)");
        }
        if (!latencyReady) return out.toString();

        out.append(" • lat");
        if (riskGapMs != Long.MIN_VALUE) {
            out.append(" gap=").append(riskGapMs).append("ms");
        }
        if (requiredMarginMs != Long.MAX_VALUE) {
            out.append(" need=").append(requiredMarginMs).append("ms");
        }
        out.append(" guard=");
        if (guardRemainingMs > 0L) {
            out.append(guardRemainingMs).append('/').append(guardDurationMs).append("ms");
        } else {
            out.append("off/").append(guardDurationMs).append("ms");
        }
        return out.toString();
    }

    private static boolean isKnown(String value) {
        return value != null && !value.isEmpty() && !"-".equals(value);
    }

    private static String labelOrDash(String value) {
        return isKnown(value) ? value : "-";
    }
}
''')

TEST.write_text('''package com.livecopilot.micprobe;

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
''')
