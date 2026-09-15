from pathlib import Path

ROOT = Path('.')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 match, found {count}')
    return text.replace(old, new, 1)

# Version bump.
build = ROOT / 'app/build.gradle.kts'
text = build.read_text()
text = replace_once(text,
    '        versionCode = 71\n        versionName = "0.70.0-routing-health-decay"',
    '        versionCode = 72\n        versionName = "0.71.0-relative-latency-routing"',
    'version')
build.write_text(text)

# Relative route-latency policy: bounded EWMA, minimum evidence and freshness probing.
policy = ROOT / 'app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java'
text = policy.read_text()
text = replace_once(text,
'''    static final long VERY_SLOW_REALTIME_LATENCY_MS = 5_000L;\n    static final long PENALTY_DECAY_STEP_MS = 15_000L;\n    static final int MAX_OUTCOME_PENALTY = 4;\n    static final int MAX_LATENCY_PENALTY = 4;\n    static final int GOOD_REALTIME_RESET_STREAK = 2;\n''',
'''    static final long VERY_SLOW_REALTIME_LATENCY_MS = 5_000L;\n    static final long PENALTY_DECAY_STEP_MS = 15_000L;\n    static final long ROUTE_LATENCY_SAMPLE_MAX_AGE_MS = 20_000L;\n    static final long ROUTE_LATENCY_FILE_MARGIN_MS = 700L;\n    static final int MAX_OUTCOME_PENALTY = 4;\n    static final int MAX_LATENCY_PENALTY = 4;\n    static final int GOOD_REALTIME_RESET_STREAK = 2;\n    static final int MIN_ROUTE_LATENCY_SAMPLES = 2;\n    static final int MAX_ROUTE_LATENCY_SAMPLES = 8;\n''', 'policy constants')
text = replace_once(text,
'''    static boolean isSlowRealtimeLatency(long latencyMs) {\n        return latencyMs > SLOW_REALTIME_LATENCY_MS;\n    }\n\n    static int decayedPenalty(int currentPenalty, long idleMs, int maxPenalty) {\n''',
'''    static boolean isSlowRealtimeLatency(long latencyMs) {\n        return latencyMs > SLOW_REALTIME_LATENCY_MS;\n    }\n\n    static long nextRouteLatencyEstimate(long currentEstimateMs, int currentSamples,\n                                         long sampleMs) {\n        if (sampleMs < 0L) return currentEstimateMs;\n        if (currentEstimateMs < 0L || currentSamples <= 0) return sampleMs;\n        return Math.max(0L, (currentEstimateMs * 3L + sampleMs + 2L) / 4L);\n    }\n\n    static int nextRouteLatencySampleCount(int currentSamples, long sampleMs) {\n        int safe = Math.max(0, Math.min(MAX_ROUTE_LATENCY_SAMPLES, currentSamples));\n        if (sampleMs < 0L) return safe;\n        return Math.min(MAX_ROUTE_LATENCY_SAMPLES, safe + 1);\n    }\n\n    static boolean shouldPreferFileForLatency(\n            long realtimeEstimateMs, int realtimeSamples, long realtimeSampleAtMs,\n            long fileEstimateMs, int fileSamples, long fileSampleAtMs, long nowMs) {\n        if (realtimeEstimateMs < 0L || fileEstimateMs < 0L) return false;\n        if (realtimeSamples < MIN_ROUTE_LATENCY_SAMPLES\n                || fileSamples < MIN_ROUTE_LATENCY_SAMPLES) return false;\n        if (!isRouteLatencyFresh(realtimeSampleAtMs, nowMs)\n                || !isRouteLatencyFresh(fileSampleAtMs, nowMs)) return false;\n        return realtimeEstimateMs - fileEstimateMs >= ROUTE_LATENCY_FILE_MARGIN_MS;\n    }\n\n    private static boolean isRouteLatencyFresh(long sampleAtMs, long nowMs) {\n        return sampleAtMs > 0L\n                && nowMs >= sampleAtMs\n                && nowMs - sampleAtMs <= ROUTE_LATENCY_SAMPLE_MAX_AGE_MS;\n    }\n\n    static int decayedPenalty(int currentPenalty, long idleMs, int maxPenalty) {\n''', 'route latency methods')
policy.write_text(text)

# Realtime client owns both route estimates because it makes the pre-turn decision.
client = ROOT / 'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java'
text = client.read_text()
text = replace_once(text,
'''    private int routingLatencyPenalty;\n    private int goodRealtimeStreak;\n    private long lastRoutingSignalAtMs;\n    private long transportBlockedUntilMs;\n''',
'''    private int routingLatencyPenalty;\n    private int goodRealtimeStreak;\n    private long lastRoutingSignalAtMs;\n    private long realtimeRouteLatencyEstimateMs = -1L;\n    private int realtimeRouteLatencySamples;\n    private long realtimeRouteLatencySampleAtMs;\n    private long fileRouteLatencyEstimateMs = -1L;\n    private int fileRouteLatencySamples;\n    private long fileRouteLatencySampleAtMs;\n    private String lastRouteDecisionLabel = "file";\n    private long transportBlockedUntilMs;\n''', 'client route fields')
text = replace_once(text,
'''        routingLatencyPenalty = 0;\n        goodRealtimeStreak = 0;\n        lastRoutingSignalAtMs = 0L;\n        transportBlockedUntilMs = 0L;\n''',
'''        routingLatencyPenalty = 0;\n        goodRealtimeStreak = 0;\n        lastRoutingSignalAtMs = 0L;\n        realtimeRouteLatencyEstimateMs = -1L;\n        realtimeRouteLatencySamples = 0;\n        realtimeRouteLatencySampleAtMs = 0L;\n        fileRouteLatencyEstimateMs = -1L;\n        fileRouteLatencySamples = 0;\n        fileRouteLatencySampleAtMs = 0L;\n        lastRouteDecisionLabel = "file";\n        transportBlockedUntilMs = 0L;\n''', 'client stop reset')
text = replace_once(text,
'''    synchronized boolean beginTurn(int sourceSampleRate) {\n        long now = System.currentTimeMillis();\n        decayRoutingPenaltiesLocked(now);\n        long blockedUntilMs = RealtimeRoutingPolicy.effectiveBlockedUntil(\n                transportBlockedUntilMs, outcomeBlockedUntilMs);\n        if (!RealtimeRoutingPolicy.shouldUseRealtime(ready, socket != null, now, blockedUntilMs)\n                || turnActive || awaitingCompletion || sourceSampleRate <= 0) return false;\n        turnActive = true;\n''',
'''    synchronized boolean beginTurn(int sourceSampleRate) {\n        long now = System.currentTimeMillis();\n        decayRoutingPenaltiesLocked(now);\n        long blockedUntilMs = RealtimeRoutingPolicy.effectiveBlockedUntil(\n                transportBlockedUntilMs, outcomeBlockedUntilMs);\n        lastRouteDecisionLabel = "file";\n        if (!RealtimeRoutingPolicy.shouldUseRealtime(ready, socket != null, now, blockedUntilMs)) {\n            if (ready && socket != null && blockedUntilMs > now) {\n                lastRouteDecisionLabel = "file-hyst";\n            }\n            return false;\n        }\n        if (turnActive || awaitingCompletion || sourceSampleRate <= 0) return false;\n        if (RealtimeRoutingPolicy.shouldPreferFileForLatency(\n                realtimeRouteLatencyEstimateMs, realtimeRouteLatencySamples,\n                realtimeRouteLatencySampleAtMs, fileRouteLatencyEstimateMs,\n                fileRouteLatencySamples, fileRouteLatencySampleAtMs, now)) {\n            lastRouteDecisionLabel = "file-perf";\n            return false;\n        }\n        lastRouteDecisionLabel = "rt";\n        turnActive = true;\n''', 'client begin routing')
text = replace_once(text,
'''    synchronized int goodRealtimeStreak() {\n        return Math.max(0, goodRealtimeStreak);\n    }\n\n    synchronized void noteRealtimeTranscriptOutcome(boolean acceptedUseful, boolean fileRecovery,\n''',
'''    synchronized int goodRealtimeStreak() {\n        return Math.max(0, goodRealtimeStreak);\n    }\n\n    synchronized long realtimeRouteLatencyEstimateMs() {\n        return realtimeRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES\n                ? realtimeRouteLatencyEstimateMs : -1L;\n    }\n\n    synchronized long fileRouteLatencyEstimateMs() {\n        return fileRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES\n                ? fileRouteLatencyEstimateMs : -1L;\n    }\n\n    synchronized String lastRouteDecisionLabel() {\n        return lastRouteDecisionLabel;\n    }\n\n    synchronized void noteRealtimeTranscriptOutcome(boolean acceptedUseful, boolean fileRecovery,\n''', 'client route getters')
text = replace_once(text,
'''        long now = System.currentTimeMillis();\n        decayRoutingPenaltiesLocked(now);\n\n        routingQualityPenalty = RealtimeRoutingPolicy.nextOutcomePenalty(\n''',
'''        long now = System.currentTimeMillis();\n        decayRoutingPenaltiesLocked(now);\n        if (!fileRecovery && latencyMs >= 0L) {\n            realtimeRouteLatencyEstimateMs = RealtimeRoutingPolicy.nextRouteLatencyEstimate(\n                    realtimeRouteLatencyEstimateMs, realtimeRouteLatencySamples, latencyMs);\n            realtimeRouteLatencySamples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(\n                    realtimeRouteLatencySamples, latencyMs);\n            realtimeRouteLatencySampleAtMs = now;\n        }\n\n        routingQualityPenalty = RealtimeRoutingPolicy.nextOutcomePenalty(\n''', 'client rt estimate update')
text = replace_once(text,
'''    synchronized void notePrimaryFileTurnOutcome(boolean usable) {\n        if (closed || !wanted) return;\n        goodRealtimeStreak = 0;\n        if (usable) return;\n        long now = System.currentTimeMillis();\n        decayRoutingPenaltiesLocked(now);\n        routingQualityPenalty = RealtimeRoutingPolicy.penaltyAfterBadFile(\n''',
'''    synchronized void notePrimaryFileTurnOutcome(boolean usable, long drainLatencyMs) {\n        if (closed || !wanted) return;\n        goodRealtimeStreak = 0;\n        long now = System.currentTimeMillis();\n        if (usable) {\n            if (drainLatencyMs >= 0L) {\n                fileRouteLatencyEstimateMs = RealtimeRoutingPolicy.nextRouteLatencyEstimate(\n                        fileRouteLatencyEstimateMs, fileRouteLatencySamples, drainLatencyMs);\n                fileRouteLatencySamples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(\n                        fileRouteLatencySamples, drainLatencyMs);\n                fileRouteLatencySampleAtMs = now;\n            }\n            return;\n        }\n        // A degraded file turn invalidates any old "file is faster" evidence so an\n        // unhealthy file lane cannot keep winning on stale performance history.\n        fileRouteLatencyEstimateMs = -1L;\n        fileRouteLatencySamples = 0;\n        fileRouteLatencySampleAtMs = 0L;\n        decayRoutingPenaltiesLocked(now);\n        routingQualityPenalty = RealtimeRoutingPolicy.penaltyAfterBadFile(\n''', 'client file estimate update')
client.write_text(text)

# Service: expose the chosen route reason, feed file drain latency, and show estimates in debug.
service = ROOT / 'app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java'
text = service.read_text()
text = replace_once(text,
'''        realtimeSpeechEpoch = realtimeTranscriber != null\n                ? realtimeTranscriber.noteNewSpeech()\n                : -1L;\n        realtimeTurnActive = realtimeTranscriber != null && realtimeTranscriber.beginTurn(sampleRate);\n        long routeBlockMs = realtimeTranscriber == null\n                ? 0L : realtimeTranscriber.routingBlockRemainingMs();\n        lastTurnSttRoute = realtimeTurnActive\n                ? "rt"\n                : (routeBlockMs > 0L ? "file-hyst" : "file");\n''',
'''        realtimeSpeechEpoch = realtimeTranscriber != null\n                ? realtimeTranscriber.noteNewSpeech()\n                : -1L;\n        realtimeTurnActive = realtimeTranscriber != null && realtimeTranscriber.beginTurn(sampleRate);\n        lastTurnSttRoute = realtimeTranscriber == null\n                ? "file" : realtimeTranscriber.lastRouteDecisionLabel();\n''', 'service route reason')
text = replace_once(text,
'''        if (realtimeTranscriber != null) {\n            realtimeTranscriber.notePrimaryFileTurnOutcome(usableFileRoute);\n        }\n''',
'''        if (realtimeTranscriber != null) {\n            realtimeTranscriber.notePrimaryFileTurnOutcome(\n                    usableFileRoute, lastFileDrainLatencyMs);\n        }\n''', 'service file latency feedback')
text = replace_once(text,
'''        int goodRealtimeStreak = realtimeTranscriber == null\n                ? 0 : realtimeTranscriber.goodRealtimeStreak();\n        String rt = " • RT " + realtimeState\n''',
'''        int goodRealtimeStreak = realtimeTranscriber == null\n                ? 0 : realtimeTranscriber.goodRealtimeStreak();\n        long realtimeRouteEstimateMs = realtimeTranscriber == null\n                ? -1L : realtimeTranscriber.realtimeRouteLatencyEstimateMs();\n        long fileRouteEstimateMs = realtimeTranscriber == null\n                ? -1L : realtimeTranscriber.fileRouteLatencyEstimateMs();\n        String rt = " • RT " + realtimeState\n''', 'service debug estimates')
text = replace_once(text,
'''                + (routeLatencyPenalty > 0 ? " • lat×" + routeLatencyPenalty : "")\n                + (goodRealtimeStreak > 0 ? " • good×" + goodRealtimeStreak : "")\n                + (routeBlockMs > 0L ? " • route-cd " + latencyLabel(routeBlockMs) : "")\n''',
'''                + (routeLatencyPenalty > 0 ? " • lat×" + routeLatencyPenalty : "")\n                + (goodRealtimeStreak > 0 ? " • good×" + goodRealtimeStreak : "")\n                + (realtimeRouteEstimateMs >= 0L\n                ? " • rt-est " + latencyLabel(realtimeRouteEstimateMs) : "")\n                + (fileRouteEstimateMs >= 0L\n                ? " • file-est " + latencyLabel(fileRouteEstimateMs) : "")\n                + (routeBlockMs > 0L ? " • route-cd " + latencyLabel(routeBlockMs) : "")\n''', 'service debug route string')
service.write_text(text)

# Regression coverage for smoothing, evidence threshold, margin and automatic RT probe expiry.
test = ROOT / 'app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingPolicyTest.java'
text = test.read_text()
insert = '''\n    @Test\n    public void routeLatencyEstimateUsesBoundedEwma() {\n        long estimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(-1L, 0, 2_400L);\n        int samples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(0, 2_400L);\n        assertEquals(2_400L, estimate);\n        assertEquals(1, samples);\n\n        estimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(estimate, samples, 1_600L);\n        samples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(samples, 1_600L);\n        assertEquals(2_200L, estimate);\n        assertEquals(2, samples);\n        assertEquals(8, RealtimeRoutingPolicy.nextRouteLatencySampleCount(8, 1_000L));\n        assertEquals(2_200L, RealtimeRoutingPolicy.nextRouteLatencyEstimate(\n                estimate, samples, -1L));\n    }\n\n    @Test\n    public void relativeLatencyNeedsEnoughFreshEvidenceAndMeaningfulMargin() {\n        long now = 100_000L;\n        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(\n                2_500L, 1, 99_000L, 1_700L, 2, 99_000L, now));\n        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(\n                2_399L, 2, 99_000L, 1_700L, 2, 99_000L, now));\n        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(\n                2_400L, 2, 99_000L, 1_700L, 2, 99_000L, now));\n        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(\n                2_400L, 2, 79_999L, 1_700L, 2, 99_000L, now));\n        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(\n                1_500L, 2, 99_000L, 1_700L, 2, 99_000L, now));\n    }\n'''
needle = '''\n    @Test\n    public void connectedSocketIsRoutableOnlyAfterBlockExpires() {\n'''
text = replace_once(text, needle, insert + needle, 'routing policy tests')
test.write_text(text)

# Guard expected production changes.
client_text = client.read_text()
if 'notePrimaryFileTurnOutcome(boolean usable)' in client_text:
    raise SystemExit('old file outcome signature remains')
if 'file-perf' not in client_text:
    raise SystemExit('performance route label missing')
print('v0.71 patch applied')
