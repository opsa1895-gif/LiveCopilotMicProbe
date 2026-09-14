from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:180]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


policy = "app/src/main/java/com/livecopilot/micprobe/RealtimeReconnectPolicy.java"
test = "app/src/test/java/com/livecopilot/micprobe/RealtimeReconnectPolicyTest.java"
client = "app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java"
gradle = "app/build.gradle.kts"

Path(policy).write_text('''package com.livecopilot.micprobe;\n\nfinal class RealtimeReconnectPolicy {\n    static final long STABLE_RESET_MS = 15_000L;\n\n    private RealtimeReconnectPolicy() {}\n\n    static long delayMs(int attempt) {\n        int safeAttempt = Math.max(0, Math.min(3, attempt));\n        return 1_000L << safeAttempt;\n    }\n}\n''', encoding="utf-8")

Path(test).write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertTrue;\n\npublic class RealtimeReconnectPolicyTest {\n    @Test\n    public void reconnectBackoffIsBounded() {\n        assertEquals(1_000L, RealtimeReconnectPolicy.delayMs(0));\n        assertEquals(2_000L, RealtimeReconnectPolicy.delayMs(1));\n        assertEquals(4_000L, RealtimeReconnectPolicy.delayMs(2));\n        assertEquals(8_000L, RealtimeReconnectPolicy.delayMs(3));\n        assertEquals(8_000L, RealtimeReconnectPolicy.delayMs(8));\n    }\n\n    @Test\n    public void negativeAttemptUsesFirstDelay() {\n        assertEquals(1_000L, RealtimeReconnectPolicy.delayMs(-5));\n    }\n\n    @Test\n    public void stableWindowIsLongerThanMaximumRetryDelay() {\n        assertEquals(15_000L, RealtimeReconnectPolicy.STABLE_RESET_MS);\n        assertTrue(RealtimeReconnectPolicy.STABLE_RESET_MS > RealtimeReconnectPolicy.delayMs(3));\n    }\n}\n''', encoding="utf-8")

replace_once(
    client,
    '''        reconnectAttempt = 0;\n        reconnectScheduled = false;\n        ready = true;\n        listener.onState("ready");\n''',
    '''        reconnectScheduled = false;\n        ready = true;\n        long stableGeneration = generation;\n        scheduler.schedule(() -> markConnectionStable(ws, stableGeneration),\n                RealtimeReconnectPolicy.STABLE_RESET_MS, TimeUnit.MILLISECONDS);\n        listener.onState("ready");\n''',
)

replace_once(
    client,
    '''                turn = committedTurnSerial;\n                awaitingCompletion = false;\n                partial.setLength(0);\n                if (!transcript.isEmpty()) {\n''',
    '''                turn = committedTurnSerial;\n                awaitingCompletion = false;\n                partial.setLength(0);\n                reconnectAttempt = 0;\n                if (!transcript.isEmpty()) {\n''',
)

replace_once(
    client,
    '''    private synchronized void scheduleReconnect() {\n''',
    '''    private synchronized void markConnectionStable(WebSocket ws, long stableGeneration) {\n        if (closed || !wanted || generation != stableGeneration || socket != ws || !ready) return;\n        reconnectAttempt = 0;\n    }\n\n    private synchronized void scheduleReconnect() {\n''',
)

replace_once(
    gradle,
    '''        versionCode = 40\n        versionName = "0.39.0-realtime-watchdog"\n''',
    '''        versionCode = 41\n        versionName = "0.40.0-reconnect-stability"\n''',
)
