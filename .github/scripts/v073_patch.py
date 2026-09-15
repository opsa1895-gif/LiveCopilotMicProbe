from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly 1 match, found {count}")
    p.write_text(text.replace(old, new, 1))

replace_once(
    "app/build.gradle.kts",
    '        versionCode = 73\n        versionName = "0.72.0-confidence-latency-routing"',
    '        versionCode = 74\n        versionName = "0.73.0-adaptive-route-probing"')

policy = "app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java"
replace_once(
    policy,
    '    static final long MAX_ROUTE_LATENCY_JITTER_MS = 5_000L;\n',
    '    static final long MAX_ROUTE_LATENCY_JITTER_MS = 5_000L;\n'
    '    static final long ROUTE_PROBE_MIN_MS = 6_000L;\n'
    '    static final long ROUTE_PROBE_DEFAULT_MS = 12_000L;\n'
    '    static final long ROUTE_PROBE_MAX_MS = 20_000L;\n'
    '    static final long ROUTE_PROBE_EARLY_MARGIN_MS = 1_200L;\n'
    '    static final long ROUTE_PROBE_STRONG_MARGIN_MS = 2_500L;\n'
    '    static final long ROUTE_PROBE_HIGH_JITTER_MS = 1_500L;\n'
    '    static final long ROUTE_PROBE_STABLE_JITTER_MS = 600L;\n')

old = '''    static boolean shouldPreferFileForLatency(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        if (realtimeSamples < MIN_ROUTE_LATENCY_SAMPLES
                || fileSamples < MIN_ROUTE_LATENCY_SAMPLES) return false;
        if (!isRouteLatencyFresh(realtimeSampleAtMs, nowMs)
                || !isRouteLatencyFresh(fileSampleAtMs, nowMs)) return false;
        long realtimeRiskMs = riskAdjustedRouteLatency(realtimeEstimateMs, realtimeJitterMs);
        long fileRiskMs = riskAdjustedRouteLatency(fileEstimateMs, fileJitterMs);
        if (realtimeRiskMs < 0L || fileRiskMs < 0L) return false;
        return realtimeRiskMs - fileRiskMs >= ROUTE_LATENCY_FILE_MARGIN_MS;
    }

    private static boolean isRouteLatencyFresh(long sampleAtMs, long nowMs) {
        return sampleAtMs > 0L
                && nowMs >= sampleAtMs
                && nowMs - sampleAtMs <= ROUTE_LATENCY_SAMPLE_MAX_AGE_MS;
    }
'''
new = '''    static long adaptiveRealtimeProbeIntervalMs(
            long realtimeEstimateMs, long realtimeJitterMs,
            long fileEstimateMs, long fileJitterMs) {
        long realtimeRiskMs = riskAdjustedRouteLatency(realtimeEstimateMs, realtimeJitterMs);
        long fileRiskMs = riskAdjustedRouteLatency(fileEstimateMs, fileJitterMs);
        if (realtimeRiskMs < 0L || fileRiskMs < 0L) return 0L;
        long riskGapMs = realtimeRiskMs - fileRiskMs;
        if (riskGapMs < ROUTE_LATENCY_FILE_MARGIN_MS) return 0L;

        long combinedJitterMs = Math.max(0L, realtimeJitterMs)
                + Math.max(0L, fileJitterMs);
        if (combinedJitterMs >= ROUTE_PROBE_HIGH_JITTER_MS
                || riskGapMs < ROUTE_PROBE_EARLY_MARGIN_MS) {
            return ROUTE_PROBE_MIN_MS;
        }
        if (riskGapMs >= ROUTE_PROBE_STRONG_MARGIN_MS
                && combinedJitterMs <= ROUTE_PROBE_STABLE_JITTER_MS) {
            return ROUTE_PROBE_MAX_MS;
        }
        return ROUTE_PROBE_DEFAULT_MS;
    }

    static long realtimeProbeRemainingMs(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        if (realtimeSamples < MIN_ROUTE_LATENCY_SAMPLES
                || fileSamples < MIN_ROUTE_LATENCY_SAMPLES) return 0L;
        if (!isRouteLatencyFresh(fileSampleAtMs, nowMs)
                || realtimeSampleAtMs <= 0L || nowMs < realtimeSampleAtMs) return 0L;
        long intervalMs = adaptiveRealtimeProbeIntervalMs(
                realtimeEstimateMs, realtimeJitterMs, fileEstimateMs, fileJitterMs);
        if (intervalMs <= 0L) return 0L;
        long elapsedMs = nowMs - realtimeSampleAtMs;
        return elapsedMs >= intervalMs ? 0L : intervalMs - elapsedMs;
    }

    static boolean shouldPreferFileForLatency(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        return realtimeProbeRemainingMs(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs) > 0L;
    }

    private static boolean isRouteLatencyFresh(long sampleAtMs, long nowMs) {
        return sampleAtMs > 0L
                && nowMs >= sampleAtMs
                && nowMs - sampleAtMs <= ROUTE_LATENCY_SAMPLE_MAX_AGE_MS;
    }
'''
replace_once(policy, old, new)

client = "app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java"
replace_once(
    client,
    '''    synchronized long fileRouteLatencyJitterMs() {
        return fileRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES
                ? fileRouteLatencyJitterMs : -1L;
    }

    synchronized String lastRouteDecisionLabel() {
''',
    '''    synchronized long fileRouteLatencyJitterMs() {
        return fileRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES
                ? fileRouteLatencyJitterMs : -1L;
    }

    synchronized long performanceProbeRemainingMs() {
        return RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                realtimeRouteLatencySamples, realtimeRouteLatencySampleAtMs,
                fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs,
                System.currentTimeMillis());
    }

    synchronized String lastRouteDecisionLabel() {
''')

service = "app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java"
replace_once(
    service,
    '''        long fileRouteJitterMs = realtimeTranscriber == null
                ? -1L : realtimeTranscriber.fileRouteLatencyJitterMs();
        String rt = " • RT " + realtimeState
''',
    '''        long fileRouteJitterMs = realtimeTranscriber == null
                ? -1L : realtimeTranscriber.fileRouteLatencyJitterMs();
        long performanceProbeMs = realtimeTranscriber == null
                ? 0L : realtimeTranscriber.performanceProbeRemainingMs();
        String rt = " • RT " + realtimeState
''')
replace_once(
    service,
    '''                + (fileRouteJitterMs >= 0L
                ? "±" + latencyLabel(fileRouteJitterMs) : "")
                + (routeBlockMs > 0L ? " • route-cd " + latencyLabel(routeBlockMs) : "")
''',
    '''                + (fileRouteJitterMs >= 0L
                ? "±" + latencyLabel(fileRouteJitterMs) : "")
                + (performanceProbeMs > 0L
                ? " • probe-in " + latencyLabel(performanceProbeMs) : "")
                + (routeBlockMs > 0L ? " • route-cd " + latencyLabel(routeBlockMs) : "")
''')

test = "app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingPolicyTest.java"
anchor = '''    @Test
    public void jitterTracksRecentAbsoluteDeviationAndIsBounded() {
'''
insert = '''    @Test
    public void adaptiveProbeCadenceTracksMarginAndJitter() {
        assertEquals(6_000L, RealtimeRoutingPolicy.adaptiveRealtimeProbeIntervalMs(
                2_400L, 100L, 1_700L, 100L));
        assertEquals(12_000L, RealtimeRoutingPolicy.adaptiveRealtimeProbeIntervalMs(
                3_200L, 400L, 1_700L, 200L));
        assertEquals(20_000L, RealtimeRoutingPolicy.adaptiveRealtimeProbeIntervalMs(
                4_200L, 200L, 1_400L, 100L));
        assertEquals(6_000L, RealtimeRoutingPolicy.adaptiveRealtimeProbeIntervalMs(
                4_200L, 1_000L, 1_400L, 700L));
        assertEquals(0L, RealtimeRoutingPolicy.adaptiveRealtimeProbeIntervalMs(
                2_000L, 100L, 1_700L, 100L));
    }

    @Test
    public void adaptiveProbeReopensRealtimeAtDerivedDeadline() {
        long realtimeSampleAt = 95_000L;
        long fileSampleAt = 99_000L;
        assertEquals(1_000L, RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                2_400L, 100L, 3, realtimeSampleAt,
                1_700L, 100L, 3, fileSampleAt, 100_000L));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 3, realtimeSampleAt,
                1_700L, 100L, 3, fileSampleAt, 100_000L));
        assertEquals(0L, RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                2_400L, 100L, 3, realtimeSampleAt,
                1_700L, 100L, 3, fileSampleAt, 101_000L));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 3, realtimeSampleAt,
                1_700L, 100L, 3, fileSampleAt, 101_000L));
    }

'''
replace_once(test, anchor, insert + anchor)

print("v0.73 adaptive route probing patch applied")
