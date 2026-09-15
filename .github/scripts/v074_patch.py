from pathlib import Path

ROOT = Path('.')

def replace_exact(path, old, new, count=1):
    p = ROOT / path
    text = p.read_text()
    actual = text.count(old)
    if actual != count:
        raise SystemExit(f'{path}: expected {count} matches, found {actual}')
    p.write_text(text.replace(old, new))

replace_exact(
    'app/build.gradle.kts',
    '        versionCode = 74\n        versionName = "0.73.0-adaptive-route-probing"',
    '        versionCode = 75\n        versionName = "0.74.0-robust-latency-learning"')

replace_exact(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java',
    '    static final long MAX_ROUTE_LATENCY_JITTER_MS = 5_000L;\n',
    '    static final long MAX_ROUTE_LATENCY_JITTER_MS = 5_000L;\n'
    '    static final long MAX_ROUTE_LATENCY_SAMPLE_MS = 12_000L;\n'
    '    static final long MAX_ROUTE_LATENCY_RISE_STEP_MS = 3_000L;\n'
    '    static final long MAX_ROUTE_LATENCY_DROP_STEP_MS = 4_000L;\n')

replace_exact(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java',
    '''    static long nextRouteLatencyEstimate(long currentEstimateMs, int currentSamples,\n                                         long sampleMs) {\n        if (sampleMs < 0L) return currentEstimateMs;\n        if (currentEstimateMs < 0L || currentSamples <= 0) return sampleMs;\n        return Math.max(0L, (currentEstimateMs * 3L + sampleMs + 2L) / 4L);\n    }\n''',
    '''    static long boundedRouteLatencySample(long currentEstimateMs, int currentSamples,\n                                          long sampleMs) {\n        if (sampleMs < 0L) return sampleMs;\n        long bounded = Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS, sampleMs);\n        if (currentEstimateMs < 0L || currentSamples <= 0) return bounded;\n\n        long safeEstimate = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS, currentEstimateMs));\n        long floor = Math.max(0L, safeEstimate - MAX_ROUTE_LATENCY_DROP_STEP_MS);\n        long ceiling = Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS,\n                safeEstimate + MAX_ROUTE_LATENCY_RISE_STEP_MS);\n        return Math.max(floor, Math.min(ceiling, bounded));\n    }\n\n    static boolean isRouteLatencyOutlier(long currentEstimateMs, int currentSamples,\n                                         long sampleMs) {\n        return sampleMs >= 0L\n                && boundedRouteLatencySample(currentEstimateMs, currentSamples, sampleMs) != sampleMs;\n    }\n\n    static long nextRouteLatencyEstimate(long currentEstimateMs, int currentSamples,\n                                         long sampleMs) {\n        if (sampleMs < 0L) return currentEstimateMs;\n        long boundedSampleMs = boundedRouteLatencySample(\n                currentEstimateMs, currentSamples, sampleMs);\n        if (currentEstimateMs < 0L || currentSamples <= 0) return boundedSampleMs;\n        long safeEstimate = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS, currentEstimateMs));\n        return Math.max(0L, (safeEstimate * 3L + boundedSampleMs + 2L) / 4L);\n    }\n''')

replace_exact(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java',
    '''    static long nextRouteLatencyJitter(long currentJitterMs, long currentEstimateMs,\n                                       int currentSamples, long sampleMs) {\n        if (sampleMs < 0L) return currentJitterMs;\n        if (currentEstimateMs < 0L || currentSamples <= 0) return 0L;\n        long deviation = Math.abs(sampleMs - currentEstimateMs);\n        if (currentSamples <= 1 || currentJitterMs < 0L) {\n            return Math.min(MAX_ROUTE_LATENCY_JITTER_MS, deviation);\n        }\n        long safeJitter = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_JITTER_MS, currentJitterMs));\n        long next = (safeJitter * 3L + deviation + 2L) / 4L;\n        return Math.min(MAX_ROUTE_LATENCY_JITTER_MS, Math.max(0L, next));\n    }\n''',
    '''    static long nextRouteLatencyJitter(long currentJitterMs, long currentEstimateMs,\n                                       int currentSamples, long sampleMs) {\n        if (sampleMs < 0L) return currentJitterMs;\n        if (currentEstimateMs < 0L || currentSamples <= 0) return 0L;\n        long safeEstimate = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS, currentEstimateMs));\n        long boundedSampleMs = boundedRouteLatencySample(\n                safeEstimate, currentSamples, sampleMs);\n        long deviation = Math.abs(boundedSampleMs - safeEstimate);\n        if (currentSamples <= 1 || currentJitterMs < 0L) {\n            return Math.min(MAX_ROUTE_LATENCY_JITTER_MS, deviation);\n        }\n        long safeJitter = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_JITTER_MS, currentJitterMs));\n        long next = (safeJitter * 3L + deviation + 2L) / 4L;\n        return Math.min(MAX_ROUTE_LATENCY_JITTER_MS, Math.max(0L, next));\n    }\n''')

path = ROOT / 'app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingPolicyTest.java'
text = path.read_text()
anchor = '''    @Test\n    public void riskAdjustedLatencyRejectsFastButSpikyFileLane() {\n'''
if text.count(anchor) != 1:
    raise SystemExit('test anchor mismatch')
insert = '''    @Test\n    public void singleExtremeSpikeIsWinsorizedInsteadOfPoisoningHistory() {\n        long estimate = 1_600L;\n        long jitter = 200L;\n        int samples = 3;\n\n        assertEquals(4_600L, RealtimeRoutingPolicy.boundedRouteLatencySample(\n                estimate, samples, 20_000L));\n        assertTrue(RealtimeRoutingPolicy.isRouteLatencyOutlier(\n                estimate, samples, 20_000L));\n\n        long nextJitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(\n                jitter, estimate, samples, 20_000L);\n        long nextEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(\n                estimate, samples, 20_000L);\n        assertEquals(2_350L, nextEstimate);\n        assertEquals(900L, nextJitter);\n        assertEquals(3_250L, RealtimeRoutingPolicy.riskAdjustedRouteLatency(\n                nextEstimate, nextJitter));\n    }\n\n    @Test\n    public void sustainedSlowdownStillMovesEstimatorUpward() {\n        long estimate = 1_600L;\n        long jitter = 200L;\n        int samples = 3;\n\n        for (int i = 0; i < 5; i++) {\n            long nextJitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(\n                    jitter, estimate, samples, 8_000L);\n            long nextEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(\n                    estimate, samples, 8_000L);\n            samples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(samples, 8_000L);\n            estimate = nextEstimate;\n            jitter = nextJitter;\n        }\n\n        assertTrue(estimate >= 5_000L);\n        assertTrue(jitter > 0L);\n    }\n\n    @Test\n    public void recoveryCanMoveFasterThanUpwardOutlierDrift() {\n        assertEquals(4_000L, RealtimeRoutingPolicy.boundedRouteLatencySample(\n                8_000L, 5, 1_000L));\n        assertEquals(5_000L, RealtimeRoutingPolicy.boundedRouteLatencySample(\n                2_000L, 5, 9_000L));\n        assertEquals(12_000L, RealtimeRoutingPolicy.boundedRouteLatencySample(\n                -1L, 0, Long.MAX_VALUE));\n        assertFalse(RealtimeRoutingPolicy.isRouteLatencyOutlier(\n                2_000L, 5, 3_000L));\n    }\n\n'''
path.write_text(text.replace(anchor, insert + anchor))
