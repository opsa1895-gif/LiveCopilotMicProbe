from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)

# Version
p = Path("app/build.gradle.kts")
s = p.read_text()
s = replace_once(s, 'versionCode = 72\n        versionName = "0.71.0-relative-latency-routing"',
                 'versionCode = 73\n        versionName = "0.72.0-confidence-latency-routing"',
                 "version")
p.write_text(s)

# Routing policy
p = Path("app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java")
s = p.read_text()
s = replace_once(s,
'''    static final long ROUTE_LATENCY_SAMPLE_MAX_AGE_MS = 20_000L;
    static final long ROUTE_LATENCY_FILE_MARGIN_MS = 700L;
''',
'''    static final long ROUTE_LATENCY_SAMPLE_MAX_AGE_MS = 20_000L;
    static final long ROUTE_LATENCY_FILE_MARGIN_MS = 700L;
    static final long MAX_ROUTE_LATENCY_JITTER_MS = 5_000L;
''', "policy jitter constant")
s = replace_once(s,
'''    static final int MIN_ROUTE_LATENCY_SAMPLES = 2;
''',
'''    static final int MIN_ROUTE_LATENCY_SAMPLES = 3;
''', "policy evidence threshold")
anchor = '''    static int nextRouteLatencySampleCount(int currentSamples, long sampleMs) {
        int safe = Math.max(0, Math.min(MAX_ROUTE_LATENCY_SAMPLES, currentSamples));
        if (sampleMs < 0L) return safe;
        return Math.min(MAX_ROUTE_LATENCY_SAMPLES, safe + 1);
    }

'''
addition = anchor + '''    static boolean isRealtimePerformanceSampleEligible(
            boolean acceptedUseful, boolean fileRecovery, long latencyMs) {
        return acceptedUseful && !fileRecovery && latencyMs >= 0L;
    }

    static long nextRouteLatencyJitter(long currentJitterMs, long currentEstimateMs,
                                       int currentSamples, long sampleMs) {
        if (sampleMs < 0L) return currentJitterMs;
        if (currentEstimateMs < 0L || currentSamples <= 0) return 0L;
        long deviation = Math.abs(sampleMs - currentEstimateMs);
        if (currentSamples <= 1 || currentJitterMs < 0L) {
            return Math.min(MAX_ROUTE_LATENCY_JITTER_MS, deviation);
        }
        long safeJitter = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_JITTER_MS, currentJitterMs));
        long next = (safeJitter * 3L + deviation + 2L) / 4L;
        return Math.min(MAX_ROUTE_LATENCY_JITTER_MS, Math.max(0L, next));
    }

    static long riskAdjustedRouteLatency(long estimateMs, long jitterMs) {
        if (estimateMs < 0L || jitterMs < 0L) return -1L;
        long safeJitter = Math.min(MAX_ROUTE_LATENCY_JITTER_MS, jitterMs);
        return estimateMs + Math.max(0L, safeJitter);
    }

'''
s = replace_once(s, anchor, addition, "policy jitter helpers")
old_pref = '''    static boolean shouldPreferFileForLatency(
            long realtimeEstimateMs, int realtimeSamples, long realtimeSampleAtMs,
            long fileEstimateMs, int fileSamples, long fileSampleAtMs, long nowMs) {
        if (realtimeEstimateMs < 0L || fileEstimateMs < 0L) return false;
        if (realtimeSamples < MIN_ROUTE_LATENCY_SAMPLES
                || fileSamples < MIN_ROUTE_LATENCY_SAMPLES) return false;
        if (!isRouteLatencyFresh(realtimeSampleAtMs, nowMs)
                || !isRouteLatencyFresh(fileSampleAtMs, nowMs)) return false;
        return realtimeEstimateMs - fileEstimateMs >= ROUTE_LATENCY_FILE_MARGIN_MS;
    }
'''
new_pref = '''    static boolean shouldPreferFileForLatency(
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
'''
s = replace_once(s, old_pref, new_pref, "policy confidence comparison")
p.write_text(s)

# Realtime client
p = Path("app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java")
s = p.read_text()
s = replace_once(s,
'''    private long realtimeRouteLatencyEstimateMs = -1L;
    private int realtimeRouteLatencySamples;
''',
'''    private long realtimeRouteLatencyEstimateMs = -1L;
    private long realtimeRouteLatencyJitterMs = -1L;
    private int realtimeRouteLatencySamples;
''', "rt jitter field")
s = replace_once(s,
'''    private long fileRouteLatencyEstimateMs = -1L;
    private int fileRouteLatencySamples;
''',
'''    private long fileRouteLatencyEstimateMs = -1L;
    private long fileRouteLatencyJitterMs = -1L;
    private int fileRouteLatencySamples;
''', "file jitter field")
s = replace_once(s,
'''        realtimeRouteLatencyEstimateMs = -1L;
        realtimeRouteLatencySamples = 0;
''',
'''        realtimeRouteLatencyEstimateMs = -1L;
        realtimeRouteLatencyJitterMs = -1L;
        realtimeRouteLatencySamples = 0;
''', "rt jitter reset")
s = replace_once(s,
'''        fileRouteLatencyEstimateMs = -1L;
        fileRouteLatencySamples = 0;
''',
'''        fileRouteLatencyEstimateMs = -1L;
        fileRouteLatencyJitterMs = -1L;
        fileRouteLatencySamples = 0;
''', "file jitter reset")
s = replace_once(s,
'''        if (RealtimeRoutingPolicy.shouldPreferFileForLatency(
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencySamples,
                realtimeRouteLatencySampleAtMs, fileRouteLatencyEstimateMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs, now)) {
''',
'''        if (RealtimeRoutingPolicy.shouldPreferFileForLatency(
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                realtimeRouteLatencySamples, realtimeRouteLatencySampleAtMs,
                fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs, now)) {
''', "beginTurn confidence args")
getter_anchor = '''    synchronized long fileRouteLatencyEstimateMs() {
        return fileRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES
                ? fileRouteLatencyEstimateMs : -1L;
    }

'''
getter_add = getter_anchor + '''    synchronized long realtimeRouteLatencyJitterMs() {
        return realtimeRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES
                ? realtimeRouteLatencyJitterMs : -1L;
    }

    synchronized long fileRouteLatencyJitterMs() {
        return fileRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES
                ? fileRouteLatencyJitterMs : -1L;
    }

'''
s = replace_once(s, getter_anchor, getter_add, "jitter getters")
old_rt_sample = '''        if (!fileRecovery && latencyMs >= 0L) {
            realtimeRouteLatencyEstimateMs = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                    realtimeRouteLatencyEstimateMs, realtimeRouteLatencySamples, latencyMs);
            realtimeRouteLatencySamples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(
                    realtimeRouteLatencySamples, latencyMs);
            realtimeRouteLatencySampleAtMs = now;
        }
'''
new_rt_sample = '''        if (RealtimeRoutingPolicy.isRealtimePerformanceSampleEligible(
                acceptedUseful, fileRecovery, latencyMs)) {
            long previousEstimateMs = realtimeRouteLatencyEstimateMs;
            int previousSamples = realtimeRouteLatencySamples;
            realtimeRouteLatencyJitterMs = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                    realtimeRouteLatencyJitterMs, previousEstimateMs, previousSamples, latencyMs);
            realtimeRouteLatencyEstimateMs = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                    previousEstimateMs, previousSamples, latencyMs);
            realtimeRouteLatencySamples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(
                    previousSamples, latencyMs);
            realtimeRouteLatencySampleAtMs = now;
        }
'''
s = replace_once(s, old_rt_sample, new_rt_sample, "rt performance sample")
old_file_sample = '''        if (performanceEligible && drainLatencyMs >= 0L) {
            fileRouteLatencyEstimateMs = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                    fileRouteLatencyEstimateMs, fileRouteLatencySamples, drainLatencyMs);
            fileRouteLatencySamples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(
                    fileRouteLatencySamples, drainLatencyMs);
            fileRouteLatencySampleAtMs = now;
        } else if (!performanceEligible) {
'''
new_file_sample = '''        if (performanceEligible && drainLatencyMs >= 0L) {
            long previousEstimateMs = fileRouteLatencyEstimateMs;
            int previousSamples = fileRouteLatencySamples;
            fileRouteLatencyJitterMs = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                    fileRouteLatencyJitterMs, previousEstimateMs, previousSamples, drainLatencyMs);
            fileRouteLatencyEstimateMs = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                    previousEstimateMs, previousSamples, drainLatencyMs);
            fileRouteLatencySamples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(
                    previousSamples, drainLatencyMs);
            fileRouteLatencySampleAtMs = now;
        } else if (!performanceEligible) {
'''
s = replace_once(s, old_file_sample, new_file_sample, "file performance sample")
s = replace_once(s,
'''            fileRouteLatencyEstimateMs = -1L;
            fileRouteLatencySamples = 0;
            fileRouteLatencySampleAtMs = 0L;
''',
'''            fileRouteLatencyEstimateMs = -1L;
            fileRouteLatencyJitterMs = -1L;
            fileRouteLatencySamples = 0;
            fileRouteLatencySampleAtMs = 0L;
''', "degraded file jitter clear")
p.write_text(s)

# Service hidden diagnostics
p = Path("app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java")
s = p.read_text()
s = replace_once(s,
'''        long fileRouteEstimateMs = realtimeTranscriber == null
                ? -1L : realtimeTranscriber.fileRouteLatencyEstimateMs();
        String rt = " • RT " + realtimeState
''',
'''        long fileRouteEstimateMs = realtimeTranscriber == null
                ? -1L : realtimeTranscriber.fileRouteLatencyEstimateMs();
        long realtimeRouteJitterMs = realtimeTranscriber == null
                ? -1L : realtimeTranscriber.realtimeRouteLatencyJitterMs();
        long fileRouteJitterMs = realtimeTranscriber == null
                ? -1L : realtimeTranscriber.fileRouteLatencyJitterMs();
        String rt = " • RT " + realtimeState
''', "debug jitter getters")
s = replace_once(s,
'''                + (realtimeRouteEstimateMs >= 0L
                ? " • rt-est " + latencyLabel(realtimeRouteEstimateMs) : "")
                + (fileRouteEstimateMs >= 0L
                ? " • file-est " + latencyLabel(fileRouteEstimateMs) : "")
''',
'''                + (realtimeRouteEstimateMs >= 0L
                ? " • rt-est " + latencyLabel(realtimeRouteEstimateMs) : "")
                + (realtimeRouteJitterMs >= 0L
                ? "±" + latencyLabel(realtimeRouteJitterMs) : "")
                + (fileRouteEstimateMs >= 0L
                ? " • file-est " + latencyLabel(fileRouteEstimateMs) : "")
                + (fileRouteJitterMs >= 0L
                ? "±" + latencyLabel(fileRouteJitterMs) : "")
''', "debug jitter display")
p.write_text(s)

# Policy tests
p = Path("app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingPolicyTest.java")
s = p.read_text()
old_relative = '''    @Test
    public void relativeLatencyNeedsEnoughFreshEvidenceAndMeaningfulMargin() {
        long now = 100_000L;
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_500L, 1, 99_000L, 1_700L, 2, 99_000L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_399L, 2, 99_000L, 1_700L, 2, 99_000L, now));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 2, 99_000L, 1_700L, 2, 99_000L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 2, 79_999L, 1_700L, 2, 99_000L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                1_500L, 2, 99_000L, 1_700L, 2, 99_000L, now));
    }
'''
new_relative = '''    @Test
    public void relativeLatencyNeedsEnoughFreshConfidenceAndMeaningfulMargin() {
        long now = 100_000L;
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_500L, 100L, 2, 99_000L, 1_700L, 100L, 3, 99_000L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_399L, 100L, 3, 99_000L, 1_700L, 100L, 3, 99_000L, now));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 3, 99_000L, 1_700L, 100L, 3, 99_000L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 3, 79_999L, 1_700L, 100L, 3, 99_000L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                1_500L, 100L, 3, 99_000L, 1_700L, 100L, 3, 99_000L, now));
    }

    @Test
    public void jitterTracksRecentAbsoluteDeviationAndIsBounded() {
        long estimate = -1L;
        long jitter = -1L;
        int samples = 0;

        jitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(jitter, estimate, samples, 2_400L);
        estimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(estimate, samples, 2_400L);
        samples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(samples, 2_400L);
        assertEquals(0L, jitter);

        jitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(jitter, estimate, samples, 1_600L);
        estimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(estimate, samples, 1_600L);
        samples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(samples, 1_600L);
        assertEquals(800L, jitter);
        assertEquals(2_200L, estimate);

        jitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(jitter, estimate, samples, 2_600L);
        assertEquals(700L, jitter);
        assertEquals(5_000L, RealtimeRoutingPolicy.nextRouteLatencyJitter(
                5_000L, 1_000L, 3, 20_000L));
    }

    @Test
    public void riskAdjustedLatencyRejectsFastButSpikyFileLane() {
        long now = 100_000L;
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_600L, 100L, 3, 99_000L, 1_600L, 100L, 3, 99_000L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_600L, 100L, 3, 99_000L, 1_600L, 800L, 3, 99_000L, now));
        assertEquals(2_700L, RealtimeRoutingPolicy.riskAdjustedRouteLatency(2_600L, 100L));
        assertEquals(2_400L, RealtimeRoutingPolicy.riskAdjustedRouteLatency(1_600L, 800L));
    }

    @Test
    public void realtimePerformanceSamplesRequireUsefulDirectText() {
        assertTrue(RealtimeRoutingPolicy.isRealtimePerformanceSampleEligible(true, false, 1_500L));
        assertFalse(RealtimeRoutingPolicy.isRealtimePerformanceSampleEligible(false, false, 1_000L));
        assertFalse(RealtimeRoutingPolicy.isRealtimePerformanceSampleEligible(true, true, 1_000L));
        assertFalse(RealtimeRoutingPolicy.isRealtimePerformanceSampleEligible(true, false, -1L));
    }
'''
s = replace_once(s, old_relative, new_relative, "confidence tests")
p.write_text(s)
