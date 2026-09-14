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
    '''        boolean sent;\n        try { sent = socket.send(event.toString()); }\n        catch (Throwable t) { sent = false; }\n        if (!sent) {\n            turnActive = false;\n            ready = false;\n            listener.onState("fallback");\n            return false;\n        }\n        sentSamples += realtime.length;\n''',
    '''        WebSocket failedSocket = socket;\n        boolean sent;\n        try { sent = failedSocket.send(event.toString()); }\n        catch (Throwable t) { sent = false; }\n        if (!sent) {\n            socket = null;\n            turnActive = false;\n            ready = false;\n            sentSamples = 0;\n            activeBackup.clear();\n            listener.onState("fallback");\n            try { failedSocket.close(1011, "append_failed"); } catch (Throwable ignored) {}\n            scheduleReconnect();\n            return false;\n        }\n        sentSamples += realtime.length;\n''',
)

replace_once(
    client,
    '''        boolean sent;\n        try { sent = socket.send(event.toString()); }\n        catch (Throwable t) { sent = false; }\n        if (!sent) {\n            sentSamples = 0;\n            ready = false;\n            activeBackup.clear();\n            listener.onState("fallback");\n            return false;\n        }\n\n        committedTurnSerial = activeTurnSerial;\n''',
    '''        WebSocket failedSocket = socket;\n        boolean sent;\n        try { sent = failedSocket.send(event.toString()); }\n        catch (Throwable t) { sent = false; }\n        if (!sent) {\n            socket = null;\n            sentSamples = 0;\n            ready = false;\n            awaitingCompletion = false;\n            activeBackup.clear();\n            listener.onState("fallback");\n            try { failedSocket.close(1011, "commit_failed"); } catch (Throwable ignored) {}\n            scheduleReconnect();\n            return false;\n        }\n\n        committedTurnSerial = activeTurnSerial;\n''',
)

replace_once(
    client,
    '''        if (!sent) {\n            ready = false;\n            listener.onState("fallback");\n            try { ws.close(1011, "session_update_failed"); } catch (Throwable ignored) {}\n            return;\n        }\n\n        reconnectAttempt = 0;\n''',
    '''        if (!sent) {\n            socket = null;\n            ready = false;\n            turnActive = false;\n            awaitingCompletion = false;\n            listener.onState("fallback");\n            try { ws.close(1011, "session_update_failed"); } catch (Throwable ignored) {}\n            scheduleReconnect();\n            return;\n        }\n\n        reconnectAttempt = 0;\n''',
)

replace_once(
    client,
    '''        if ("error".equals(type)) {\n            long recoveryTurn = -1L;\n            long recoveryGeneration = -1L;\n            synchronized (this) {\n                if (ws != socket) return;\n                if (awaitingCompletion) {\n                    recoveryTurn = committedTurnSerial;\n                    recoveryGeneration = generation;\n                }\n                ready = false;\n                turnActive = false;\n                awaitingCompletion = false;\n            }\n            listener.onState("fallback");\n            if (recoveryTurn > 0L) recoverCommittedTurn(recoveryTurn, recoveryGeneration, "server_error");\n        }\n''',
    '''        if ("error".equals(type)) {\n            long recoveryTurn = -1L;\n            long recoveryGeneration = -1L;\n            WebSocket failedSocket;\n            synchronized (this) {\n                if (ws != socket) return;\n                if (awaitingCompletion) {\n                    recoveryTurn = committedTurnSerial;\n                    recoveryGeneration = generation;\n                }\n                failedSocket = socket;\n                socket = null;\n                ready = false;\n                turnActive = false;\n                awaitingCompletion = false;\n            }\n            listener.onState("fallback");\n            if (failedSocket != null) {\n                try { failedSocket.close(1011, "server_error"); } catch (Throwable ignored) {}\n            }\n            if (recoveryTurn > 0L) recoverCommittedTurn(recoveryTurn, recoveryGeneration, "server_error");\n            scheduleReconnect();\n        }\n''',
)

replace_once(
    client,
    '''        long delay = Math.min(10_000L, 1_000L << Math.min(3, reconnectAttempt++));\n''',
    '''        long delay = RealtimeReconnectPolicy.delayMs(reconnectAttempt++);\n''',
)

replace_once(
    gradle,
    '''        versionCode = 38\n        versionName = "0.37.0-quiet-speech-vad"\n''',
    '''        versionCode = 39\n        versionName = "0.38.0-realtime-recovery"\n''',
)
