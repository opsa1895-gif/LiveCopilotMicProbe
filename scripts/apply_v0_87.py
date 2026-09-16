from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


policy_path = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java')
policy = policy_path.read_text()
old_release = '''    static boolean shouldReleaseRouteFlapHistoryForRegimeChange(
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
new_release = '''    static String routeFlapHistoryReleaseReasonForRegimeChange(
            int routeFlapScore, int changedRoute, int outlierDirection, int outlierStreak,
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        if (routeFlapScore <= 0
                || !isRouteLatencyRegimeChange(outlierDirection, outlierStreak)) return "-";

        String reason;
        boolean fileFavoringShift;
        if (changedRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME && outlierDirection > 0) {
            reason = "rt-slowdown";
            fileFavoringShift = true;
        } else if (changedRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME && outlierDirection < 0) {
            reason = "rt-speedup";
            fileFavoringShift = false;
        } else if (changedRoute == ROUTE_PERFORMANCE_ROUTE_FILE && outlierDirection < 0) {
            reason = "file-speedup";
            fileFavoringShift = true;
        } else if (changedRoute == ROUTE_PERFORMANCE_ROUTE_FILE && outlierDirection > 0) {
            reason = "file-slowdown";
            fileFavoringShift = false;
        } else {
            return "-";
        }
        if (!isRouteLatencyFresh(realtimeSampleAtMs, nowMs)
                || !isRouteLatencyFresh(fileSampleAtMs, nowMs)) return "-";

        long riskGapMs = routeLatencyRiskGapMs(
                realtimeEstimateMs, realtimeJitterMs, fileEstimateMs, fileJitterMs);
        long baselineMarginMs = routeLatencyPreferenceMarginMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
        if (riskGapMs == Long.MIN_VALUE || baselineMarginMs == Long.MAX_VALUE) return "-";
        boolean qualifies = fileFavoringShift
                ? riskGapMs >= baselineMarginMs
                : riskGapMs < baselineMarginMs;
        return qualifies ? reason : "-";
    }

    static boolean shouldReleaseRouteFlapHistoryForRegimeChange(
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
policy = replace_once(policy, old_release, new_release, 'policy release reason helper')
policy_path.write_text(policy)

client_path = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java')
client = client_path.read_text()
old_fields = '''    private int lastPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
    private long lastPerformanceRouteDecisionAtMs;
    private long transportBlockedUntilMs;
'''
new_fields = '''    private int lastPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
    private long lastPerformanceRouteDecisionAtMs;
    private String lastRouteFlapReleaseReason = "-";
    private long lastRouteFlapReleaseAtMs;
    private long transportBlockedUntilMs;
'''
client = replace_once(client, old_fields, new_fields, 'client release diagnostic fields')

old_stop = '''        previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
        lastPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
        lastPerformanceRouteDecisionAtMs = 0L;
        transportBlockedUntilMs = 0L;
'''
new_stop = '''        previousPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
        lastPerformanceRoute = RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_UNKNOWN;
        lastPerformanceRouteDecisionAtMs = 0L;
        lastRouteFlapReleaseReason = "-";
        lastRouteFlapReleaseAtMs = 0L;
        transportBlockedUntilMs = 0L;
'''
client = replace_once(client, old_stop, new_stop, 'client release diagnostic reset')

old_maybe = '''    private void maybeReleaseRouteFlapHistoryForLatencyRegimeLocked(
            int changedRoute, int outlierDirection, int outlierStreak, long nowMs) {
        int activeFlapScore = RealtimeRoutingPolicy.activeRouteFlapScore(
                routeFlapScore, lastPerformanceRouteDecisionAtMs, nowMs);
        if (!RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                activeFlapScore, changedRoute, outlierDirection, outlierStreak,
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                realtimeRouteLatencySamples, realtimeRouteLatencySampleAtMs,
                fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs, nowMs)) return;
        clearRouteFlapHistoryLocked(true);
    }
'''
new_maybe = '''    private void maybeReleaseRouteFlapHistoryForLatencyRegimeLocked(
            int changedRoute, int outlierDirection, int outlierStreak, long nowMs) {
        int activeFlapScore = RealtimeRoutingPolicy.activeRouteFlapScore(
                routeFlapScore, lastPerformanceRouteDecisionAtMs, nowMs);
        String releaseReason = RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                activeFlapScore, changedRoute, outlierDirection, outlierStreak,
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                realtimeRouteLatencySamples, realtimeRouteLatencySampleAtMs,
                fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs, nowMs);
        if ("-".equals(releaseReason)) return;
        lastRouteFlapReleaseReason = releaseReason;
        lastRouteFlapReleaseAtMs = nowMs;
        clearRouteFlapHistoryLocked(true);
    }
'''
client = replace_once(client, old_maybe, new_maybe, 'client release reason capture')

old_diag = '''        StringBuilder out = new StringBuilder("route why ").append(lastRouteDecisionReason)
                .append(" • n ").append(realtimeRouteLatencySamples)
                .append('/').append(fileRouteLatencySamples);
        boolean routeHistoryFresh = RealtimeRoutingPolicy.isRouteFlapHistoryFresh(
'''
new_diag = '''        StringBuilder out = new StringBuilder("route why ").append(lastRouteDecisionReason)
                .append(" • n ").append(realtimeRouteLatencySamples)
                .append('/').append(fileRouteLatencySamples);
        if (!"-".equals(lastRouteFlapReleaseReason)
                && RealtimeRoutingPolicy.isRouteFlapHistoryFresh(lastRouteFlapReleaseAtMs, now)) {
            out.append(" • release ").append(lastRouteFlapReleaseReason);
        }
        boolean routeHistoryFresh = RealtimeRoutingPolicy.isRouteFlapHistoryFresh(
'''
client = replace_once(client, old_diag, new_diag, 'client release reason diagnostics')
client_path.write_text(client)

build_path = Path('app/build.gradle.kts')
build = build_path.read_text()
build = replace_once(
    build,
    '        versionCode = 87\n        versionName = "0.86.0-symmetric-regime-release"\n',
    '        versionCode = 88\n        versionName = "0.87.0-regime-release-observability"\n',
    'version bump')
build_path.write_text(build)

test_path = Path('app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingRegimeFlapReleasePolicyTest.java')
test = test_path.read_text()
test = replace_once(
    test,
    'import static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n',
    'import static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n',
    'test assertEquals import')
insert = '''
    @Test
    public void releaseReasonNamesRealtimeAndFileRegimeDirections() {
        assertEquals("rt-slowdown", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 99_000L, NOW));
        assertEquals("file-speedup", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_000L,
                1_600L, 100L, 4, 99_500L, NOW));
        assertEquals("rt-speedup", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_500L, 100L, 4, 99_500L,
                1_700L, 100L, 4, 99_000L, NOW));
        assertEquals("file-slowdown", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_800L, 100L, 4, 99_000L,
                2_300L, 100L, 4, 99_500L, NOW));
    }

    @Test
    public void releaseReasonIsHiddenWhenRegimeShiftDoesNotQualify() {
        assertEquals("-", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_000L, 100L, 4, 99_500L,
                1_500L, 100L, 4, 99_000L, NOW));
        assertEquals("-", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_100L, 100L, 4, 99_500L,
                1_200L, 100L, 4, 99_000L, NOW));
        assertEquals("-", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                -1, 1,
                2_600L, 100L, 4, 99_000L,
                1_600L, 100L, 4, 99_500L, NOW));
    }
'''
test = replace_once(test, '\n}\n', insert + '\n}\n', 'test release reason coverage')
test_path.write_text(test)
