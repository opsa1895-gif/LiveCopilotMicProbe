from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


policy_path = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java')
policy = policy_path.read_text()
old_helper = '''    static boolean shouldReleaseRouteFlapHistoryForRegimeChange(
            int routeFlapScore, int changedRoute, int outlierDirection, int outlierStreak,
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        if (routeFlapScore <= 0
                || !isRouteLatencyRegimeChange(outlierDirection, outlierStreak)) return false;
        boolean fileFavoringShift = changedRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                ? outlierDirection > 0
                : changedRoute == ROUTE_PERFORMANCE_ROUTE_FILE && outlierDirection < 0;
        if (!fileFavoringShift) return false;
        if (!isRouteLatencyFresh(realtimeSampleAtMs, nowMs)
                || !isRouteLatencyFresh(fileSampleAtMs, nowMs)) return false;
        return hasConfidentFileLatencyAdvantage(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples,
                fileEstimateMs, fileJitterMs, fileSamples);
    }
'''
new_helper = '''    static boolean shouldReleaseRouteFlapHistoryForRegimeChange(
            int routeFlapScore, int changedRoute, int outlierDirection, int outlierStreak,
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        if (routeFlapScore <= 0
                || !isRouteLatencyRegimeChange(outlierDirection, outlierStreak)) return false;
        boolean fileFavoringShift = changedRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                ? outlierDirection > 0
                : changedRoute == ROUTE_PERFORMANCE_ROUTE_FILE && outlierDirection < 0;
        boolean realtimeFavoringShift = changedRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                ? outlierDirection < 0
                : changedRoute == ROUTE_PERFORMANCE_ROUTE_FILE && outlierDirection > 0;
        if (!fileFavoringShift && !realtimeFavoringShift) return false;
        if (!isRouteLatencyFresh(realtimeSampleAtMs, nowMs)
                || !isRouteLatencyFresh(fileSampleAtMs, nowMs)) return false;

        long riskGapMs = routeLatencyRiskGapMs(
                realtimeEstimateMs, realtimeJitterMs, fileEstimateMs, fileJitterMs);
        long baselineMarginMs = routeLatencyPreferenceMarginMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
        if (riskGapMs == Long.MIN_VALUE || baselineMarginMs == Long.MAX_VALUE) return false;
        if (fileFavoringShift) return riskGapMs >= baselineMarginMs;
        return riskGapMs < baselineMarginMs;
    }
'''
policy = replace_once(policy, old_helper, new_helper, 'symmetric regime release helper')
policy_path.write_text(policy)

build_path = Path('app/build.gradle.kts')
build = build_path.read_text()
build = replace_once(
    build,
    '        versionCode = 86\n        versionName = "0.85.0-regime-aware-flap-release"\n',
    '        versionCode = 87\n        versionName = "0.86.0-symmetric-regime-release"\n',
    'version bump')
build_path.write_text(build)

test_path = Path('app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingRegimeFlapReleasePolicyTest.java')
test = test_path.read_text().rstrip()
if not test.endswith('}'):
    raise SystemExit('regime test file: missing closing brace')
test = test[:-1] + '''

    @Test
    public void confirmedRealtimeSpeedupReleasesStaleFilePreferenceHistory() {
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_500L, 100L, 4, 99_500L,
                1_700L, 100L, 4, 99_000L, NOW));
    }

    @Test
    public void confirmedFileSlowdownReleasesStaleFilePreferenceHistory() {
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                1, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_800L, 100L, 4, 99_000L,
                2_300L, 100L, 4, 99_500L, NOW));
    }

    @Test
    public void realtimeFavoringShiftKeepsDampingWhileFileStillWinsBaseline() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_100L, 100L, 4, 99_500L,
                1_200L, 100L, 4, 99_000L, NOW));
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_100L, 100L, 4, 99_000L,
                1_200L, 100L, 4, 99_500L, NOW));
    }

    @Test
    public void realtimeReleaseStillRequiresUsableConfidenceInputs() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_500L, 100L, 2, 99_500L,
                1_700L, 100L, 4, 99_000L, NOW));
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_500L, -1L, 4, 99_500L,
                1_700L, 100L, 4, 99_000L, NOW));
    }
}
'''
test_path.write_text(test)
