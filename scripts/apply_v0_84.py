from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


policy_path = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java')
policy = policy_path.read_text()
policy = replace_once(
    policy,
    '    static final int ROUTE_FLAP_MAX_SCORE = 3;\n',
    '    static final int ROUTE_FLAP_MAX_SCORE = 3;\n'
    '    static final int ROUTE_FLAP_STABLE_CONFIRM_TURNS = 3;\n',
    'stable route constant')
policy = replace_once(
    policy,
    '''    static int activeRouteFlapScore(int currentScore, long lastDecisionAtMs, long nowMs) {\n        int safe = Math.max(0, Math.min(ROUTE_FLAP_MAX_SCORE, currentScore));\n        if (safe <= 0) return 0;\n        if (lastDecisionAtMs <= 0L || nowMs < lastDecisionAtMs\n                || nowMs - lastDecisionAtMs > ROUTE_FLAP_WINDOW_MS) return 0;\n        return safe;\n    }\n''',
    '''    static boolean isRouteFlapHistoryFresh(long lastDecisionAtMs, long nowMs) {\n        return lastDecisionAtMs > 0L\n                && nowMs >= lastDecisionAtMs\n                && nowMs - lastDecisionAtMs <= ROUTE_FLAP_WINDOW_MS;\n    }\n\n    static int activeRouteFlapScore(int currentScore, long lastDecisionAtMs, long nowMs) {\n        int safe = Math.max(0, Math.min(ROUTE_FLAP_MAX_SCORE, currentScore));\n        if (safe <= 0) return 0;\n        return isRouteFlapHistoryFresh(lastDecisionAtMs, nowMs) ? safe : 0;\n    }\n''',
    'history freshness helper')
marker = '''    static boolean isRouteFlapReversal(\n            int previousDistinctRoute, int currentRoute, int nextRoute) {\n'''
stable_helpers = '''    static int nextStablePerformanceRouteStreak(\n            int currentStreak, int currentRoute, int nextRoute) {\n        boolean currentKnown = currentRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME\n                || currentRoute == ROUTE_PERFORMANCE_ROUTE_FILE;\n        boolean nextKnown = nextRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME\n                || nextRoute == ROUTE_PERFORMANCE_ROUTE_FILE;\n        if (!nextKnown) return 0;\n        if (!currentKnown || nextRoute != currentRoute) return 1;\n        int safe = Math.max(1, Math.min(ROUTE_FLAP_STABLE_CONFIRM_TURNS, currentStreak));\n        return Math.min(ROUTE_FLAP_STABLE_CONFIRM_TURNS, safe + 1);\n    }\n\n    static boolean shouldReleaseRouteFlapHistory(int stableRouteStreak) {\n        return stableRouteStreak >= ROUTE_FLAP_STABLE_CONFIRM_TURNS;\n    }\n\n'''
policy = replace_once(policy, marker, stable_helpers + marker, 'stable route helpers')
policy_path.write_text(policy)

client_path = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java')
client = client_path.read_text()
client = replace_once(
    client,
    '''    private int routeFlapScore;\n    private int previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n''',
    '''    private int routeFlapScore;\n    private int stablePerformanceRouteStreak;\n    private int previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n''',
    'stable route client field')
client = replace_once(
    client,
    '''        routeFlapScore = 0;\n        previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n''',
    '''        routeFlapScore = 0;\n        stablePerformanceRouteStreak = 0;\n        previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n''',
    'stable route reset')
old_note = '''    private void notePerformanceRouteDecisionLocked(int route, String reason, long nowMs) {\n        if (!RealtimeRoutingPolicy.isTrackableRouteDecisionReason(reason)) return;\n        routeFlapScore = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(\n                routeFlapScore, previousPerformanceRoute, lastPerformanceRoute,\n                lastPerformanceRouteDecisionAtMs, route, nowMs);\n        if (lastPerformanceRoute == RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN) {\n            lastPerformanceRoute = route;\n        } else if (route != lastPerformanceRoute) {\n            previousPerformanceRoute = lastPerformanceRoute;\n            lastPerformanceRoute = route;\n        }\n        lastPerformanceRouteDecisionAtMs = nowMs;\n    }\n'''
new_note = '''    private void notePerformanceRouteDecisionLocked(int route, String reason, long nowMs) {\n        if (!RealtimeRoutingPolicy.isTrackableRouteDecisionReason(reason)) return;\n        if (!RealtimeRoutingPolicy.isRouteFlapHistoryFresh(\n                lastPerformanceRouteDecisionAtMs, nowMs)) {\n            routeFlapScore = 0;\n            stablePerformanceRouteStreak = 0;\n            previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n            lastPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n        }\n\n        int nextStableStreak = RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(\n                stablePerformanceRouteStreak, lastPerformanceRoute, route);\n        routeFlapScore = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(\n                routeFlapScore, previousPerformanceRoute, lastPerformanceRoute,\n                lastPerformanceRouteDecisionAtMs, route, nowMs);\n        if (lastPerformanceRoute == RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN) {\n            lastPerformanceRoute = route;\n        } else if (route != lastPerformanceRoute) {\n            previousPerformanceRoute = lastPerformanceRoute;\n            lastPerformanceRoute = route;\n        }\n        stablePerformanceRouteStreak = nextStableStreak;\n        lastPerformanceRouteDecisionAtMs = nowMs;\n        if (RealtimeRoutingPolicy.shouldReleaseRouteFlapHistory(stablePerformanceRouteStreak)) {\n            routeFlapScore = 0;\n            previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n        }\n    }\n'''
client = replace_once(client, old_note, new_note, 'stable route client update')
old_diag = '''        String routeHistory = RealtimeRoutingPolicy.performanceRouteHistoryLabel(\n                previousPerformanceRoute, lastPerformanceRoute);\n        if (!"-".equals(routeHistory)) {\n            out.append(" • hist ").append(routeHistory);\n        }\n        if (activeFlapScore > 0) {\n'''
new_diag = '''        boolean routeHistoryFresh = RealtimeRoutingPolicy.isRouteFlapHistoryFresh(\n                lastPerformanceRouteDecisionAtMs, now);\n        String routeHistory = routeHistoryFresh\n                ? RealtimeRoutingPolicy.performanceRouteHistoryLabel(\n                        previousPerformanceRoute, lastPerformanceRoute)\n                : "-";\n        if (!"-".equals(routeHistory)) {\n            out.append(" • hist ").append(routeHistory);\n            if (stablePerformanceRouteStreak > 0) {\n                out.append(" • stable×").append(stablePerformanceRouteStreak)\n                        .append('/').append(RealtimeRoutingPolicy.ROUTE_FLAP_STABLE_CONFIRM_TURNS);\n            }\n        }\n        if (activeFlapScore > 0) {\n'''
client = replace_once(client, old_diag, new_diag, 'stable route diagnostics')
client_path.write_text(client)

build_path = Path('app/build.gradle.kts')
build = build_path.read_text()
build = replace_once(
    build,
    '        versionCode = 84\n        versionName = "0.83.0-route-reversal-memory"\n',
    '        versionCode = 85\n        versionName = "0.84.0-stable-route-release"\n',
    'version bump')
build_path.write_text(build)

test_path = Path('app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingStableRouteReleasePolicyTest.java')
test_path.write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n\npublic class RealtimeRoutingStableRouteReleasePolicyTest {\n    @Test\n    public void stableRouteStreakConfirmsAfterThreeTurns() {\n        int streak = RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(\n                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN,\n                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE);\n        assertEquals(1, streak);\n        streak = RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(\n                streak, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,\n                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE);\n        assertEquals(2, streak);\n        streak = RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(\n                streak, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,\n                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE);\n        assertEquals(RealtimeRoutingPolicy.ROUTE_FLAP_STABLE_CONFIRM_TURNS, streak);\n        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistory(streak));\n    }\n\n    @Test\n    public void switchingRoutesRestartsStableStreak() {\n        assertEquals(1, RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(\n                RealtimeRoutingPolicy.ROUTE_FLAP_STABLE_CONFIRM_TURNS,\n                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,\n                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME));\n        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistory(2));\n    }\n\n    @Test\n    public void stableRunReleasesOldReversalTarget() {\n        int score = 2;\n        int streak = 1;\n        int previous = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE;\n        int current = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME;\n\n        score = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(\n                score, previous, current, 1_000L, current, 2_000L);\n        streak = RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(streak, current, current);\n        assertEquals(1, score);\n        assertEquals(2, streak);\n\n        score = RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(\n                score, previous, current, 2_000L, current, 3_000L);\n        streak = RealtimeRoutingPolicy.nextStablePerformanceRouteStreak(streak, current, current);\n        assertEquals(0, score);\n        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistory(streak));\n\n        previous = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;\n        assertEquals(0, RealtimeRoutingPolicy.nextRouteFlapScoreFromHistory(\n                score, previous, current, 3_000L,\n                RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE, 4_000L));\n    }\n\n    @Test\n    public void idleHistoryIsExplicitlyStale() {\n        assertTrue(RealtimeRoutingPolicy.isRouteFlapHistoryFresh(1_000L, 21_000L));\n        assertFalse(RealtimeRoutingPolicy.isRouteFlapHistoryFresh(1_000L, 21_001L));\n        assertFalse(RealtimeRoutingPolicy.isRouteFlapHistoryFresh(2_000L, 1_999L));\n        assertEquals(0, RealtimeRoutingPolicy.activeRouteFlapScore(\n                RealtimeRoutingPolicy.ROUTE_FLAP_MAX_SCORE, 1_000L, 21_001L));\n    }\n}\n''')
