from pathlib import Path


def replace_once(path, old, new, label):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    p.write_text(text.replace(old, new, 1))

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java',
'''    static int nextLatencyPenalty(int currentPenalty, long latencyMs) {\n        int safe = clampPenalty(currentPenalty, MAX_LATENCY_PENALTY);\n        if (latencyMs < 0L) return safe;\n        if (latencyMs <= FAST_REALTIME_LATENCY_MS) return Math.max(0, safe - 1);\n        if (latencyMs <= SLOW_REALTIME_LATENCY_MS) return safe;\n        if (latencyMs <= VERY_SLOW_REALTIME_LATENCY_MS) {\n            return Math.min(MAX_LATENCY_PENALTY, safe + 1);\n        }\n        return Math.min(MAX_LATENCY_PENALTY, safe + 2);\n    }\n''',
'''    static int nextLatencyPenalty(int currentPenalty, long latencyMs) {\n        int safe = clampPenalty(currentPenalty, MAX_LATENCY_PENALTY);\n        if (latencyMs < 0L) return safe;\n        if (latencyMs <= FAST_REALTIME_LATENCY_MS) return Math.max(0, safe - 1);\n        if (latencyMs <= SLOW_REALTIME_LATENCY_MS) return safe;\n        if (latencyMs <= VERY_SLOW_REALTIME_LATENCY_MS) {\n            return Math.min(MAX_LATENCY_PENALTY, safe + 1);\n        }\n        return Math.min(MAX_LATENCY_PENALTY, safe + 2);\n    }\n\n    static boolean isSlowRealtimeLatency(long latencyMs) {\n        return latencyMs > SLOW_REALTIME_LATENCY_MS;\n    }\n''',
    'policy slow latency helper')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
'''        int previousLatencyPenalty = routingLatencyPenalty;\n        routingQualityPenalty = RealtimeRoutingPolicy.nextOutcomePenalty(\n''',
'''        routingQualityPenalty = RealtimeRoutingPolicy.nextOutcomePenalty(\n''',
    'remove previous latency score')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
'''        int effectivePenalty = RealtimeRoutingPolicy.effectiveOutcomePenalty(\n                routingQualityPenalty, routingLatencyPenalty);\n        boolean latencyWorsened = routingLatencyPenalty > previousLatencyPenalty;\n        if (acceptedUseful && !fileRecovery && !latencyWorsened) {\n''',
'''        int effectivePenalty = RealtimeRoutingPolicy.effectiveOutcomePenalty(\n                routingQualityPenalty, routingLatencyPenalty);\n        boolean slowRealtimeLatency = !fileRecovery\n                && RealtimeRoutingPolicy.isSlowRealtimeLatency(latencyMs);\n        if (acceptedUseful && !fileRecovery && !slowRealtimeLatency) {\n''',
    'slow latency keeps hold')

replace_once(
    'app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingPolicyTest.java',
'''    @Test\n    public void stalePenaltiesDecayWithoutNewBadSignals() {\n''',
'''    @Test\n    public void slowLatencyRemainsActionableEvenWhenScoreIsSaturated() {\n        assertFalse(RealtimeRoutingPolicy.isSlowRealtimeLatency(-1L));\n        assertFalse(RealtimeRoutingPolicy.isSlowRealtimeLatency(3_000L));\n        assertTrue(RealtimeRoutingPolicy.isSlowRealtimeLatency(3_001L));\n        assertEquals(4, RealtimeRoutingPolicy.nextLatencyPenalty(4, 4_000L));\n    }\n\n    @Test\n    public void stalePenaltiesDecayWithoutNewBadSignals() {\n''',
    'slow latency regression test')

print('v0.70 slow-latency hold refinement applied')
