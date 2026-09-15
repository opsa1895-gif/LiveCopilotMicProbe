from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}, got {count}")
    p.write_text(text.replace(old, new, 1))


# Keep connection-instability quarantine and transcript-quality quarantine independent.
replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
    '''    private int unstableReadyFailureStreak;\n    private int routingOutcomePenalty;\n    private long routingBlockedUntilMs;\n    private long latestRecoveryId;''',
    '''    private int unstableReadyFailureStreak;\n    private int routingOutcomePenalty;\n    private long transportBlockedUntilMs;\n    private long outcomeBlockedUntilMs;\n    private long latestRecoveryId;''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
    '''        unstableReadyFailureStreak = 0;\n        routingOutcomePenalty = 0;\n        routingBlockedUntilMs = 0L;''',
    '''        unstableReadyFailureStreak = 0;\n        routingOutcomePenalty = 0;\n        transportBlockedUntilMs = 0L;\n        outcomeBlockedUntilMs = 0L;''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
    '''    synchronized boolean beginTurn(int sourceSampleRate) {\n        long now = System.currentTimeMillis();\n        if (!RealtimeRoutingPolicy.shouldUseRealtime(ready, socket != null, now, routingBlockedUntilMs)\n                || turnActive || awaitingCompletion || sourceSampleRate <= 0) return false;''',
    '''    synchronized boolean beginTurn(int sourceSampleRate) {\n        long now = System.currentTimeMillis();\n        long blockedUntilMs = RealtimeRoutingPolicy.effectiveBlockedUntil(\n                transportBlockedUntilMs, outcomeBlockedUntilMs);\n        if (!RealtimeRoutingPolicy.shouldUseRealtime(ready, socket != null, now, blockedUntilMs)\n                || turnActive || awaitingCompletion || sourceSampleRate <= 0) return false;''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
    '''    synchronized long routingBlockRemainingMs() {\n        if (routingBlockedUntilMs <= 0L) return 0L;\n        return Math.max(0L, routingBlockedUntilMs - System.currentTimeMillis());\n    }\n\n    synchronized int unstableReadyFailureStreak() {''',
    '''    synchronized long routingBlockRemainingMs() {\n        long blockedUntilMs = RealtimeRoutingPolicy.effectiveBlockedUntil(\n                transportBlockedUntilMs, outcomeBlockedUntilMs);\n        if (blockedUntilMs <= 0L) return 0L;\n        return Math.max(0L, blockedUntilMs - System.currentTimeMillis());\n    }\n\n    synchronized long transportBlockRemainingMs() {\n        if (transportBlockedUntilMs <= 0L) return 0L;\n        return Math.max(0L, transportBlockedUntilMs - System.currentTimeMillis());\n    }\n\n    synchronized long outcomeBlockRemainingMs() {\n        if (outcomeBlockedUntilMs <= 0L) return 0L;\n        return Math.max(0L, outcomeBlockedUntilMs - System.currentTimeMillis());\n    }\n\n    synchronized int unstableReadyFailureStreak() {''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
    '''    synchronized void noteRealtimeTranscriptOutcome(boolean acceptedUseful, boolean fileRecovery) {\n        if (closed || !wanted) return;\n        routingOutcomePenalty = RealtimeRoutingPolicy.nextOutcomePenalty(\n                routingOutcomePenalty, acceptedUseful, fileRecovery);\n        if (acceptedUseful && !fileRecovery) return;\n\n        long blockMs = RealtimeRoutingPolicy.blockMsForOutcomePenalty(routingOutcomePenalty);\n        if (blockMs <= 0L) return;\n        long now = System.currentTimeMillis();\n        routingBlockedUntilMs = Math.max(routingBlockedUntilMs, now + blockMs);\n    }\n\n    synchronized void notePrimaryFileTurnOutcome(boolean usable) {\n        if (closed || !wanted || usable) return;\n        long now = System.currentTimeMillis();\n        routingOutcomePenalty = RealtimeRoutingPolicy.penaltyAfterBadFile(\n                routingOutcomePenalty);\n        routingBlockedUntilMs = RealtimeRoutingPolicy.shortenBlockAfterBadFile(\n                now, routingBlockedUntilMs);\n    }''',
    '''    synchronized void noteRealtimeTranscriptOutcome(boolean acceptedUseful, boolean fileRecovery) {\n        if (closed || !wanted) return;\n        routingOutcomePenalty = RealtimeRoutingPolicy.nextOutcomePenalty(\n                routingOutcomePenalty, acceptedUseful, fileRecovery);\n        long now = System.currentTimeMillis();\n        if (acceptedUseful && !fileRecovery) {\n            if (routingOutcomePenalty <= 0 || outcomeBlockedUntilMs <= now) {\n                outcomeBlockedUntilMs = 0L;\n            }\n            return;\n        }\n\n        long blockMs = RealtimeRoutingPolicy.blockMsForOutcomePenalty(routingOutcomePenalty);\n        if (blockMs <= 0L) return;\n        outcomeBlockedUntilMs = Math.max(outcomeBlockedUntilMs, now + blockMs);\n    }\n\n    synchronized void notePrimaryFileTurnOutcome(boolean usable) {\n        if (closed || !wanted || usable) return;\n        long now = System.currentTimeMillis();\n        routingOutcomePenalty = RealtimeRoutingPolicy.penaltyAfterBadFile(\n                routingOutcomePenalty);\n        outcomeBlockedUntilMs = RealtimeRoutingPolicy.shortenOutcomeBlockAfterBadFile(\n                now, outcomeBlockedUntilMs);\n    }''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
    '''            if (blockMs > 0L) {\n                routingBlockedUntilMs = Math.max(routingBlockedUntilMs, now + blockMs);\n            }\n        } else if (readyAtMs > 0L) {\n            unstableReadyFailureStreak = 0;\n            routingBlockedUntilMs = 0L;\n        }''',
    '''            if (blockMs > 0L) {\n                transportBlockedUntilMs = Math.max(transportBlockedUntilMs, now + blockMs);\n            }\n        } else if (readyAtMs > 0L) {\n            unstableReadyFailureStreak = 0;\n            transportBlockedUntilMs = 0L;\n        }''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
    '''        reconnectAttempt = 0;\n        unstableReadyFailureStreak = 0;\n        routingBlockedUntilMs = 0L;''',
    '''        reconnectAttempt = 0;\n        unstableReadyFailureStreak = 0;\n        transportBlockedUntilMs = 0L;''')

# Policy helpers make the separation explicit and regression-testable.
replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java',
    '''    static long shortenBlockAfterBadFile(long nowMs, long blockedUntilMs) {\n        if (blockedUntilMs <= nowMs) return blockedUntilMs;\n        return Math.min(blockedUntilMs, nowMs + BAD_FILE_PROBE_MS);\n    }''',
    '''    static long shortenOutcomeBlockAfterBadFile(long nowMs, long outcomeBlockedUntilMs) {\n        if (outcomeBlockedUntilMs <= nowMs) return outcomeBlockedUntilMs;\n        return Math.min(outcomeBlockedUntilMs, nowMs + BAD_FILE_PROBE_MS);\n    }\n\n    static long effectiveBlockedUntil(long transportBlockedUntilMs, long outcomeBlockedUntilMs) {\n        return Math.max(Math.max(0L, transportBlockedUntilMs),\n                Math.max(0L, outcomeBlockedUntilMs));\n    }''')

# Split debug countdowns so field reports identify the active quarantine source.
replace_once(
    'app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java',
    '''        long routeBlockMs = realtimeTranscriber == null\n                ? 0L : realtimeTranscriber.routingBlockRemainingMs();\n        int unstableRt = realtimeTranscriber == null''',
    '''        long routeBlockMs = realtimeTranscriber == null\n                ? 0L : realtimeTranscriber.routingBlockRemainingMs();\n        long transportBlockMs = realtimeTranscriber == null\n                ? 0L : realtimeTranscriber.transportBlockRemainingMs();\n        long outcomeBlockMs = realtimeTranscriber == null\n                ? 0L : realtimeTranscriber.outcomeBlockRemainingMs();\n        int unstableRt = realtimeTranscriber == null''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java',
    '''                + (routeOutcomePenalty > 0 ? " • outcome×" + routeOutcomePenalty : "")\n                + (routeBlockMs > 0L ? " • route-cd " + latencyLabel(routeBlockMs) : "");''',
    '''                + (routeOutcomePenalty > 0 ? " • outcome×" + routeOutcomePenalty : "")\n                + (routeBlockMs > 0L ? " • route-cd " + latencyLabel(routeBlockMs) : "")\n                + (transportBlockMs > 0L ? " • net-cd " + latencyLabel(transportBlockMs) : "")\n                + (outcomeBlockMs > 0L ? " • quality-cd " + latencyLabel(outcomeBlockMs) : "");''')

# Version bump.
replace_once(
    'app/build.gradle.kts',
    '''        versionCode = 69\n        versionName = "0.68.0-adaptive-stt-routing"''',
    '''        versionCode = 70\n        versionName = "0.69.0-isolated-route-quarantines"''')

# Extend routing regression tests with quarantine isolation.
test_path = Path('app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingPolicyTest.java')
test = test_path.read_text()
test = test.replace(
    '''                RealtimeRoutingPolicy.shortenBlockAfterBadFile(10_000L, 25_000L));\n        assertEquals(9_000L,\n                RealtimeRoutingPolicy.shortenBlockAfterBadFile(10_000L, 9_000L));''',
    '''                RealtimeRoutingPolicy.shortenOutcomeBlockAfterBadFile(10_000L, 25_000L));\n        assertEquals(9_000L,\n                RealtimeRoutingPolicy.shortenOutcomeBlockAfterBadFile(10_000L, 9_000L));''')
anchor = '''    @Test\n    public void fileOutcomeNeedsUsefulFocusAndCoverage() {'''
insert = '''    @Test\n    public void transportAndOutcomeQuarantinesStayIndependent() {\n        long transportUntil = 25_000L;\n        long outcomeUntil = 25_000L;\n        long shortenedOutcome = RealtimeRoutingPolicy.shortenOutcomeBlockAfterBadFile(\n                10_000L, outcomeUntil);\n\n        assertEquals(11_000L, shortenedOutcome);\n        assertEquals(25_000L, RealtimeRoutingPolicy.effectiveBlockedUntil(\n                transportUntil, shortenedOutcome));\n        assertEquals(25_000L, RealtimeRoutingPolicy.effectiveBlockedUntil(\n                11_000L, 25_000L));\n        assertEquals(0L, RealtimeRoutingPolicy.effectiveBlockedUntil(-1L, 0L));\n    }\n\n'''
if test.count(anchor) != 1:
    raise SystemExit('test anchor mismatch')
test_path.write_text(test.replace(anchor, insert + anchor, 1))
