from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:180]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


client = "app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java"
gradle = "app/build.gradle.kts"

replace_once(
    client,
    '''    private static final long STT_CONTEXT_IDLE_RESET_MS = 45_000L;\n    private static final long COMMIT_TIMEOUT_MS = 9_000L;\n''',
    '''    private static final long STT_CONTEXT_IDLE_RESET_MS = 45_000L;\n''',
)

replace_once(
    client,
    '''        scheduler.schedule(() -> handleCommitTimeout(timeoutTurn, timeoutGeneration),\n                COMMIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);\n''',
    '''        scheduler.schedule(() -> handleCommitTimeout(timeoutTurn, timeoutGeneration, false),\n                RealtimeCommitPolicy.SOFT_TIMEOUT_MS, TimeUnit.MILLISECONDS);\n''',
)

replace_once(
    client,
    '''    private void handleCommitTimeout(long turn, long timeoutGeneration) {\n        WebSocket old;\n        synchronized (this) {\n            if (closed || !wanted || generation != timeoutGeneration) return;\n            if (!awaitingCompletion || committedTurnSerial != turn) return;\n            awaitingCompletion = false;\n            ready = false;\n            turnActive = false;\n            old = socket;\n            socket = null;\n        }\n\n        listener.onState("fallback");\n        if (old != null) {\n            try { old.close(1011, "transcription_timeout"); } catch (Throwable ignored) {}\n        }\n        recoverCommittedTurn(turn, timeoutGeneration, "timeout");\n        scheduleReconnect();\n    }\n''',
    '''    private void handleCommitTimeout(long turn, long timeoutGeneration, boolean finalDeadline) {\n        WebSocket old;\n        synchronized (this) {\n            if (closed || !wanted || generation != timeoutGeneration) return;\n            if (!awaitingCompletion || committedTurnSerial != turn) return;\n            if (RealtimeCommitPolicy.grantPartialGrace(finalDeadline, partial.length() > 0)) {\n                scheduler.schedule(() -> handleCommitTimeout(turn, timeoutGeneration, true),\n                        RealtimeCommitPolicy.PARTIAL_GRACE_MS, TimeUnit.MILLISECONDS);\n                return;\n            }\n            awaitingCompletion = false;\n            ready = false;\n            turnActive = false;\n            partial.setLength(0);\n            old = socket;\n            socket = null;\n        }\n\n        listener.onPartial("");\n        listener.onState("fallback");\n        if (old != null) {\n            try { old.close(1011, "transcription_timeout"); } catch (Throwable ignored) {}\n        }\n        recoverCommittedTurn(turn, timeoutGeneration, "timeout");\n        scheduleReconnect();\n    }\n''',
)

replace_once(
    client,
    '''        try { ws.send(update.toString()); } catch (Throwable ignored) {}\n    }\n\n    private synchronized void scheduleReconnect() {\n''',
    '''        boolean sent;\n        try { sent = ws.send(update.toString()); }\n        catch (Throwable ignored) { sent = false; }\n        if (sent) return;\n\n        synchronized (this) {\n            if (ws != socket || closed || !wanted) return;\n            socket = null;\n            ready = false;\n            turnActive = false;\n            awaitingCompletion = false;\n        }\n        listener.onState("fallback");\n        try { ws.close(1011, "context_update_failed"); } catch (Throwable ignored) {}\n        scheduleReconnect();\n    }\n\n    private synchronized void scheduleReconnect() {\n''',
)

replace_once(
    gradle,
    '''        versionCode = 39\n        versionName = "0.38.0-realtime-recovery"\n''',
    '''        versionCode = 40\n        versionName = "0.39.0-realtime-watchdog"\n''',
)
