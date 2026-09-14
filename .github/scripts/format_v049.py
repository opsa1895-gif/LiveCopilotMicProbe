from pathlib import Path

p = Path("app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java")
s = p.read_text()

s = s.replace(
"""        if (!ready || socket == null || sentSamples < MIN_COMMIT_SAMPLES) {
    WebSocket failedSocket = null;
    boolean clearFailed = ready && socket != null && !sendClearLocked();
    sentSamples = 0;
    activeBackup.clear();
    partial.setLength(0);
    listener.onPartial("");
    if (clearFailed) {
        failedSocket = socket;
        socket = null;
        ready = false;
        awaitingCompletion = false;
        listener.onState("fallback");
        try { failedSocket.close(1011, "clear_failed"); } catch (Throwable ignored) {}
        scheduleReconnect();
    }
    return false;
}
""",
"""        if (!ready || socket == null || sentSamples < MIN_COMMIT_SAMPLES) {
            WebSocket failedSocket = null;
            boolean clearFailed = ready && socket != null && !sendClearLocked();
            sentSamples = 0;
            activeBackup.clear();
            partial.setLength(0);
            listener.onPartial("");
            if (clearFailed) {
                failedSocket = socket;
                socket = null;
                ready = false;
                awaitingCompletion = false;
                listener.onState("fallback");
                try { failedSocket.close(1011, "clear_failed"); } catch (Throwable ignored) {}
                scheduleReconnect();
            }
            return false;
        }
""",
1,
)

s = s.replace(
"""    private boolean sendClearLocked() {
    if (socket == null) return false;
    try {
        JSONObject event = new JSONObject();
        event.put("type", "input_audio_buffer.clear");
        return socket.send(event.toString());
    } catch (Throwable ignored) {
        return false;
    }
}
""",
"""    private boolean sendClearLocked() {
        if (socket == null) return false;
        try {
            JSONObject event = new JSONObject();
            event.put("type", "input_audio_buffer.clear");
            return socket.send(event.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }
""",
1,
)

p.write_text(s)
