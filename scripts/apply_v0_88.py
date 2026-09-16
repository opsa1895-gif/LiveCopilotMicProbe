from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# Version bump.
path = Path("app/build.gradle.kts")
text = path.read_text()
text = replace_once(
    text,
    '        versionCode = 88\n        versionName = "0.87.0-regime-release-observability"',
    '        versionCode = 89\n        versionName = "0.88.0-regime-release-outcomes"',
    "version",
)
path.write_text(text)


# Pure policy helpers for observing what happens after a regime-triggered release.
path = Path("app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java")
text = path.read_text()
needle = '''    static boolean shouldReleaseRouteFlapHistoryForRegimeChange(
            int routeFlapScore, int changedRoute, int outlierDirection, int outlierStreak,
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        return !"-".equals(routeFlapHistoryReleaseReasonForRegimeChange(
                routeFlapScore, changedRoute, outlierDirection, outlierStreak,
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs));
    }

'''
replacement = needle + '''    static int routeFlapReleaseTargetRoute(String releaseReason) {
        if ("rt-slowdown".equals(releaseReason) || "file-speedup".equals(releaseReason)) {
            return ROUTE_PERFORMANCE_ROUTE_FILE;
        }
        if ("rt-speedup".equals(releaseReason) || "file-slowdown".equals(releaseReason)) {
            return ROUTE_PERFORMANCE_ROUTE_REALTIME;
        }
        return ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
    }

    static int nextRouteFlapReleaseStableStreak(
            int currentStreak, int targetRoute, long releaseAtMs,
            int nextRoute, long nowMs) {
        boolean targetKnown = targetRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || targetRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean nextKnown = nextRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || nextRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        if (!targetKnown || !nextKnown || !isRouteFlapHistoryFresh(releaseAtMs, nowMs)
                || nextRoute != targetRoute) return 0;
        int safe = Math.max(0, Math.min(ROUTE_FLAP_STABLE_CONFIRM_TURNS, currentStreak));
        return Math.min(ROUTE_FLAP_STABLE_CONFIRM_TURNS, safe + 1);
    }

    static String routeFlapReleaseOutcome(
            int targetRoute, long releaseAtMs, int currentStableStreak,
            int nextRoute, long nowMs) {
        boolean targetKnown = targetRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || targetRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean nextKnown = nextRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || nextRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        if (!targetKnown || !nextKnown) return "-";
        if (!isRouteFlapHistoryFresh(releaseAtMs, nowMs)) return "expired";
        if (nextRoute != targetRoute) return "reversal";
        int nextStableStreak = nextRouteFlapReleaseStableStreak(
                currentStableStreak, targetRoute, releaseAtMs, nextRoute, nowMs);
        return nextStableStreak >= ROUTE_FLAP_STABLE_CONFIRM_TURNS ? "stable" : "pending";
    }

'''
text = replace_once(text, needle, replacement, "policy outcome helpers")
path.write_text(text)


# Client-side session metrics only; routing decisions remain unchanged.
path = Path("app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java")
text = path.read_text()
text = replace_once(
    text,
    '''    private String lastRouteFlapReleaseReason = "-";
    private long lastRouteFlapReleaseAtMs;
    private long transportBlockedUntilMs;
''',
    '''    private String lastRouteFlapReleaseReason = "-";
    private long lastRouteFlapReleaseAtMs;
    private int lastRouteFlapReleaseTarget = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
    private int lastRouteFlapReleaseStableStreak;
    private String lastRouteFlapReleaseOutcome = "-";
    private int routeFlapReleaseStableCount;
    private int routeFlapReleaseReversalCount;
    private int routeFlapReleaseExpiredCount;
    private long transportBlockedUntilMs;
''',
    "client fields",
)
text = replace_once(
    text,
    '''        lastRouteFlapReleaseReason = "-";
        lastRouteFlapReleaseAtMs = 0L;
        transportBlockedUntilMs = 0L;
''',
    '''        lastRouteFlapReleaseReason = "-";
        lastRouteFlapReleaseAtMs = 0L;
        lastRouteFlapReleaseTarget = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
        lastRouteFlapReleaseStableStreak = 0;
        lastRouteFlapReleaseOutcome = "-";
        routeFlapReleaseStableCount = 0;
        routeFlapReleaseReversalCount = 0;
        routeFlapReleaseExpiredCount = 0;
        transportBlockedUntilMs = 0L;
''',
    "client stop reset",
)
text = replace_once(
    text,
    '''    private void notePerformanceRouteDecisionLocked(int route, String reason, long nowMs) {
        if (!RealtimeRoutingPolicy.isTrackableRouteDecisionReason(reason)) return;
        if (!RealtimeRoutingPolicy.isRouteFlapHistoryFresh(
''',
    '''    private void notePerformanceRouteDecisionLocked(int route, String reason, long nowMs) {
        if (!RealtimeRoutingPolicy.isTrackableRouteDecisionReason(reason)) return;
        noteRouteFlapReleaseOutcomeLocked(route, nowMs);
        if (!RealtimeRoutingPolicy.isRouteFlapHistoryFresh(
''',
    "observe eligible route decision",
)
needle = '''    private void maybeReleaseRouteFlapHistoryForLatencyRegimeLocked(
            int changedRoute, int outlierDirection, int outlierStreak, long nowMs) {
'''
helper = '''    private void noteRouteFlapReleaseOutcomeLocked(int route, long nowMs) {
        if (!"pending".equals(lastRouteFlapReleaseOutcome)) return;
        String outcome = RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                lastRouteFlapReleaseTarget, lastRouteFlapReleaseAtMs,
                lastRouteFlapReleaseStableStreak, route, nowMs);
        if ("-".equals(outcome)) return;
        if ("expired".equals(outcome)) {
            lastRouteFlapReleaseStableStreak = 0;
            lastRouteFlapReleaseOutcome = outcome;
            routeFlapReleaseExpiredCount++;
            return;
        }
        if ("reversal".equals(outcome)) {
            lastRouteFlapReleaseStableStreak = 0;
            lastRouteFlapReleaseOutcome = outcome;
            routeFlapReleaseReversalCount++;
            return;
        }
        lastRouteFlapReleaseStableStreak = RealtimeRoutingPolicy.nextRouteFlapReleaseStableStreak(
                lastRouteFlapReleaseStableStreak, lastRouteFlapReleaseTarget,
                lastRouteFlapReleaseAtMs, route, nowMs);
        lastRouteFlapReleaseOutcome = outcome;
        if ("stable".equals(outcome)) routeFlapReleaseStableCount++;
    }

'''
text = replace_once(text, needle, helper + needle, "client outcome tracker")
text = replace_once(
    text,
    '''        lastRouteFlapReleaseReason = releaseReason;
        lastRouteFlapReleaseAtMs = nowMs;
        clearRouteFlapHistoryLocked(true);
''',
    '''        lastRouteFlapReleaseReason = releaseReason;
        lastRouteFlapReleaseAtMs = nowMs;
        lastRouteFlapReleaseTarget = RealtimeRoutingPolicy.routeFlapReleaseTargetRoute(releaseReason);
        lastRouteFlapReleaseStableStreak = 0;
        lastRouteFlapReleaseOutcome = "pending";
        clearRouteFlapHistoryLocked(true);
''',
    "start release observation",
)
text = replace_once(
    text,
    '''        if (!"-".equals(lastRouteFlapReleaseReason)
                && RealtimeRoutingPolicy.isRouteFlapHistoryFresh(lastRouteFlapReleaseAtMs, now)) {
            out.append(" • release ").append(lastRouteFlapReleaseReason);
        }
        boolean routeHistoryFresh = RealtimeRoutingPolicy.isRouteFlapHistoryFresh(
''',
    '''        boolean releaseFresh = RealtimeRoutingPolicy.isRouteFlapHistoryFresh(
                lastRouteFlapReleaseAtMs, now);
        if (!"-".equals(lastRouteFlapReleaseReason) && releaseFresh) {
            out.append(" • release ").append(lastRouteFlapReleaseReason)
                    .append(' ').append(lastRouteFlapReleaseOutcome);
            if ("pending".equals(lastRouteFlapReleaseOutcome)) {
                out.append('×').append(lastRouteFlapReleaseStableStreak)
                        .append('/').append(RealtimeRoutingPolicy.ROUTE_FLAP_STABLE_CONFIRM_TURNS);
            }
        }
        if (routeFlapReleaseStableCount > 0 || routeFlapReleaseReversalCount > 0
                || routeFlapReleaseExpiredCount > 0) {
            out.append(" • rel s").append(routeFlapReleaseStableCount)
                    .append("/r").append(routeFlapReleaseReversalCount)
                    .append("/x").append(routeFlapReleaseExpiredCount);
        }
        boolean routeHistoryFresh = RealtimeRoutingPolicy.isRouteFlapHistoryFresh(
''',
    "diagnostics outcome metrics",
)
path.write_text(text)


# Pure unit coverage for post-release observation semantics.
test = Path("app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingRegimeReleaseOutcomePolicyTest.java")
if test.exists():
    raise RuntimeError("outcome test already exists")
test.write_text('''package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RealtimeRoutingRegimeReleaseOutcomePolicyTest {
    private static final long RELEASE_AT = 100_000L;

    @Test
    public void releaseReasonMapsToExpectedTargetRoute() {
        assertEquals(RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.routeFlapReleaseTargetRoute("rt-slowdown"));
        assertEquals(RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.routeFlapReleaseTargetRoute("file-speedup"));
        assertEquals(RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RealtimeRoutingPolicy.routeFlapReleaseTargetRoute("rt-speedup"));
        assertEquals(RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RealtimeRoutingPolicy.routeFlapReleaseTargetRoute("file-slowdown"));
        assertEquals(RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RealtimeRoutingPolicy.routeFlapReleaseTargetRoute("-"));
    }

    @Test
    public void targetRouteMustHoldForThreeTrackableTurnsToBecomeStable() {
        int target = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE;
        assertEquals("pending", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                target, RELEASE_AT, 0, target, RELEASE_AT + 1_000L));
        int streak = RealtimeRoutingPolicy.nextRouteFlapReleaseStableStreak(
                0, target, RELEASE_AT, target, RELEASE_AT + 1_000L);
        assertEquals(1, streak);
        assertEquals("pending", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                target, RELEASE_AT, streak, target, RELEASE_AT + 2_000L));
        streak = RealtimeRoutingPolicy.nextRouteFlapReleaseStableStreak(
                streak, target, RELEASE_AT, target, RELEASE_AT + 2_000L);
        assertEquals(2, streak);
        assertEquals("stable", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                target, RELEASE_AT, streak, target, RELEASE_AT + 3_000L));
    }

    @Test
    public void oppositeTrackableRouteIsQuickReversal() {
        assertEquals("reversal", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RELEASE_AT, 1, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT + 2_000L));
        assertEquals(0, RealtimeRoutingPolicy.nextRouteFlapReleaseStableStreak(
                1, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RELEASE_AT, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT + 2_000L));
    }

    @Test
    public void staleObservationExpiresInsteadOfClassifyingRoute() {
        assertEquals("expired", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT, 2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT + RealtimeRoutingPolicy.ROUTE_FLAP_WINDOW_MS + 1L));
        assertEquals(0, RealtimeRoutingPolicy.nextRouteFlapReleaseStableStreak(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RELEASE_AT + RealtimeRoutingPolicy.ROUTE_FLAP_WINDOW_MS + 1L));
    }

    @Test
    public void unknownReleaseTargetDoesNotCreateOutcome() {
        assertEquals("-", RealtimeRoutingPolicy.routeFlapReleaseOutcome(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RELEASE_AT, 0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RELEASE_AT + 1_000L));
    }
}
''')
