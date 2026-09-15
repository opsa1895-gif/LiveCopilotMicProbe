from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


policy_path = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java')
policy = policy_path.read_text()
old_policy_method = '''    static int nextRouteFlapScore(int currentScore, int previousRoute,
                                  long previousDecisionAtMs, int nextRoute, long nowMs) {
        int safe = Math.max(0, Math.min(ROUTE_FLAP_MAX_SCORE, currentScore));
        boolean previousRouteKnown = previousRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || previousRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean nextRouteKnown = nextRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || nextRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean previousFresh = previousDecisionAtMs > 0L
                && nowMs >= previousDecisionAtMs
                && nowMs - previousDecisionAtMs <= ROUTE_FLAP_WINDOW_MS;
        if (!nextRouteKnown) return activeRouteFlapScore(safe, previousDecisionAtMs, nowMs);
        if (!previousRouteKnown || !previousFresh) return 0;
        if (nextRoute != previousRoute) {
            return Math.min(ROUTE_FLAP_MAX_SCORE, safe + 1);
        }
        return Math.max(0, safe - 1);
    }
'''
new_policy_method = old_policy_method + '''
    static int nextRouteFlapScoreFromHistory(
            int currentScore, int previousDistinctRoute, int currentRoute,
            long currentDecisionAtMs, int nextRoute, long nowMs) {
        int safe = Math.max(0, Math.min(ROUTE_FLAP_MAX_SCORE, currentScore));
        boolean currentRouteKnown = currentRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || currentRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean nextRouteKnown = nextRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || nextRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean currentFresh = currentDecisionAtMs > 0L
                && nowMs >= currentDecisionAtMs
                && nowMs - currentDecisionAtMs <= ROUTE_FLAP_WINDOW_MS;
        if (!nextRouteKnown) return activeRouteFlapScore(safe, currentDecisionAtMs, nowMs);
        if (!currentRouteKnown || !currentFresh) return 0;
        if (nextRoute == currentRoute) return Math.max(0, safe - 1);
        if (isRouteFlapReversal(previousDistinctRoute, currentRoute, nextRoute)) {
            return Math.min(ROUTE_FLAP_MAX_SCORE, safe + 1);
        }
        return safe;
    }

    static boolean isRouteFlapReversal(
            int previousDistinctRoute, int currentRoute, int nextRoute) {
        boolean previousKnown = previousDistinctRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || previousDistinctRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean currentKnown = currentRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || currentRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean nextKnown = nextRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || nextRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        return previousKnown && currentKnown && nextKnown
                && previousDistinctRoute != currentRoute
                && nextRoute != currentRoute
                && nextRoute == previousDistinctRoute;
    }

    static String performanceRouteHistoryLabel(int previousDistinctRoute, int currentRoute) {
        String current = performanceRouteLabel(currentRoute);
        if ("-".equals(current)) return current;
        String previous = performanceRouteLabel(previousDistinctRoute);
        if ("-".equals(previous) || previous.equals(current)) return current;
        return previous + ">" + current;
    }

    private static String performanceRouteLabel(int route) {
        if (route == ROUTE_PERFORMANCE_ROUTE_REALTIME) return "rt";
        if (route == ROUTE_PERFORMANCE_ROUTE_FILE) return "file";
        return "-";
    }
'''
policy = replace_once(policy, old_policy_method, new_policy_method, 'policy flap history methods')
policy_path.write_text(policy)

client_path = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java')
client = client_path.read_text()
client = replace_once(
    client,
    '''    private int routeFlapScore;\n    private int lastPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n    private long lastPerformanceRouteDecisionAtMs;\n''',
    '''    private int routeFlapScore;\n    private int previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n    private int lastPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n    private long lastPerformanceRouteDecisionAtMs;\n''',
    'client route history fields')
client = replace_once(
    client,
    '''        routeFlapScore = 0;\n        lastPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n        lastPerformanceRouteDecisionAtMs = 0L;\n''',
    '''        routeFlapScore = 0;\n        previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n        lastPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n        lastPerformanceRouteDecisionAtMs = 0L;\n''',
    'client route history reset')
old_note = '''    private void notePerformanceRouteDecisionLocked(int route, String reason, long nowMs) {
        if (!RealtimeRoutingPolicy.isTrackableRouteDecisionReason(reason)) return;
        routeFlapScore = RealtimeRoutingPolicy.nextRouteFlapScore(
                routeFlapScore, lastPerformanceRoute, lastPerformanceRouteDecisionAtMs,
                route, nowMs);
        lastPerformanceRoute = route;
        lastPerformanceRouteDecisionAtMs = nowMs;
    }
'''
new_note = '''    private void notePerformanceRouteDecisionLocked(int route, String reason, long nowMs) {
        if (!RealtimeRoutingPolicy.isTrackableRouteDecisionReason(reason)) return;
        routeFlapScore = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                routeFlapScore, previousPerformanceRoute, lastPerformanceRoute,
                lastPerformanceRouteDecisionAtMs, route, nowMs);
        if (lastPerformanceRoute == RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN) {
            lastPerformanceRoute = route;
        } else if (route != lastPerformanceRoute) {
            previousPerformanceRoute = lastPerformanceRoute;
            lastPerformanceRoute = route;
        }
        lastPerformanceRouteDecisionAtMs = nowMs;
    }
'''
client = replace_once(client, old_note, new_note, 'client route history update')
old_diag = '''        StringBuilder out = new StringBuilder("route why ").append(lastRouteDecisionReason)
                .append(" • n ").append(realtimeRouteLatencySamples)
                .append('/').append(fileRouteLatencySamples);
        if (activeFlapScore > 0) {
'''
new_diag = '''        StringBuilder out = new StringBuilder("route why ").append(lastRouteDecisionReason)
                .append(" • n ").append(realtimeRouteLatencySamples)
                .append('/').append(fileRouteLatencySamples);
        String routeHistory = RealtimeRoutingPolicy.performanceRouteHistoryLabel(
                previousPerformanceRoute, lastPerformanceRoute);
        if (!"-".equals(routeHistory)) {
            out.append(" • hist ").append(routeHistory);
        }
        if (activeFlapScore > 0) {
'''
client = replace_once(client, old_diag, new_diag, 'client route history diagnostics')
client_path.write_text(client)

build_path = Path('app/build.gradle.kts')
build = build_path.read_text()
build = replace_once(
    build,
    '        versionCode = 83\n        versionName = "0.82.0-route-flap-damping"\n',
    '        versionCode = 84\n        versionName = "0.83.0-route-reversal-memory"\n',
    'version bump')
build_path.write_text(build)

test_path = Path('app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingRouteReversalMemoryPolicyTest.java')
test_path.write_text('''package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingRouteReversalMemoryPolicyTest {
    @Test
    public void singleOneWaySwitchDoesNotRaiseFlapScore() {
        assertEquals(0, RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 1_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L));
        assertFalse(RealtimeRoutingPolicy.isRouteFlapReversal(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME));
    }

    @Test
    public void quickReturnToPreviousRouteConfirmsFlapping() {
        assertTrue(RealtimeRoutingPolicy.isRouteFlapReversal(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE));
        assertEquals(1, RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 3_000L));
    }

    @Test
    public void continuedAlternationRaisesScoreToCap() {
        int score = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 3_000L);
        score = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                score, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 3_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 4_000L);
        score = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                score, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 4_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 5_000L);
        assertEquals(RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE, score);
    }

    @Test
    public void stableChoiceDecaysAndStaleHistoryCannotConfirmReversal() {
        assertEquals(2, RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 2_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 3_000L));
        assertEquals(0, RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME, 1_000L,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 21_001L));
    }

    @Test
    public void diagnosticsExposeTwoRouteHistoryWithoutInventingUnknownRoute() {
        assertEquals("file>rt", RealtimeRoutingPolicy.performanceRouteHistoryLabel(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME));
        assertEquals("file", RealtimeRoutingPolicy.performanceRouteHistoryLabel(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE));
        assertEquals("-", RealtimeRoutingPolicy.performanceRouteHistoryLabel(
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,
                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN));
    }
}
''')
