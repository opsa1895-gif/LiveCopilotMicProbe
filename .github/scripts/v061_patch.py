from pathlib import Path


def one(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 occurrence, found {count}")
    return text.replace(old, new, 1)


p = Path("app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java")
s = p.read_text()

s = one(
    s,
    "        void onFileTurnComplete(long sessionSerial, long inputSerial, String turnFocus, boolean mainReplyQueued);",
    "        void onFileTurnComplete(long sessionSerial, long inputSerial, String turnFocus,\n"
    "                                boolean mainReplyQueued, long drainLatencyMs);",
    "listener signature",
)
s = one(
    s,
    "    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();",
    "    private final OrderedParallelExecutor<String> audioExecutor =\n"
    "            new OrderedParallelExecutor<>(2);",
    "audio executor",
)
s = one(
    s,
    "    synchronized void resetSession() {\n"
    "        sessionSerial++;\n"
    "        audioHttp.cancelAll();",
    "    synchronized void resetSession() {\n"
    "        sessionSerial++;\n"
    "        audioExecutor.cancelPending();\n"
    "        audioHttp.cancelAll();",
    "reset cancellation",
)
s = one(
    s,
    "        fileTurnSerial = latestInputSerial;\n"
    "        fileTurnFocus = \"\";\n"
    "        audioHttp.cancelAll();\n"
    "        replyHttp.cancelAll();\n"
    "        return latestInputSerial;",
    "        fileTurnSerial = latestInputSerial;\n"
    "        fileTurnFocus = \"\";\n"
    "        audioExecutor.cancelPending();\n"
    "        audioHttp.cancelAll();\n"
    "        replyHttp.cancelAll();\n"
    "        return latestInputSerial;",
    "new speech cancellation",
)
s = one(
    s,
    "    synchronized void invalidatePendingWork() {\n"
    "        if (closed) return;\n"
    "        // Preserve rolling conversation context across a short pause, but make every\n"
    "        // queued/in-flight file STT and reply callback from before the pause stale.\n"
    "        latestInputSerial++;\n"
    "        latestReplySerial++;\n"
    "        fileTurnSerial = -1L;\n"
    "        fileTurnFocus = \"\";\n"
    "        audioHttp.cancelAll();\n"
    "        replyHttp.cancelAll();\n"
    "    }",
    "    synchronized void invalidatePendingWork() {\n"
    "        if (closed) return;\n"
    "        // Preserve rolling conversation context across a short pause, but make every\n"
    "        // queued/in-flight file STT and reply callback from before the pause stale.\n"
    "        latestInputSerial++;\n"
    "        latestReplySerial++;\n"
    "        fileTurnSerial = -1L;\n"
    "        fileTurnFocus = \"\";\n"
    "        audioExecutor.cancelPending();\n"
    "        audioHttp.cancelAll();\n"
    "        replyHttp.cancelAll();\n"
    "    }",
    "pause invalidation",
)
s = one(
    s,
    "            latestInputSerial++;\n"
    "            latestReplySerial++;\n"
    "            fileTurnSerial = -1L;\n"
    "            fileTurnFocus = \"\";\n"
    "            audioHttp.cancelAll();\n"
    "            replyHttp.cancelAll();\n"
    "            if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;",
    "            latestInputSerial++;\n"
    "            latestReplySerial++;\n"
    "            fileTurnSerial = -1L;\n"
    "            fileTurnFocus = \"\";\n"
    "            audioExecutor.cancelPending();\n"
    "            audioHttp.cancelAll();\n"
    "            replyHttp.cancelAll();\n"
    "            if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;",
    "realtime acceptance invalidation",
)
s = one(
    s,
    "        AudioItem item = new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial);\n"
    "        audioExecutor.execute(() -> processAudioIfFresh(item));",
    "        AudioItem item = new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial);\n"
    "        audioExecutor.submit(\n"
    "                () -> transcribeAudioIfFresh(item),\n"
    "                (raw, error) -> processAudioResult(item, raw, error));",
    "parallel submit",
)
s = one(
    s,
    "        audioExecutor.execute(() -> finishAudioTurnIfFresh(end));",
    "        audioExecutor.afterSubmitted(() -> finishAudioTurnIfFresh(end));",
    "ordered turn marker",
)

start = s.index("    private void processAudioIfFresh(AudioItem item) {")
end = s.index("    private void finishAudioTurnIfFresh(TurnEndItem end) {")
replacement = '''    private String transcribeAudioIfFresh(AudioItem item) throws Exception {
        if (item == null || !shouldProcessAudioResult(item)) return null;
        String key = SecretStore.loadApiKey(context);
        if (key.isEmpty()) {
            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Няма API key");
            return null;
        }
        listener.onStatus(item.sessionSerial, item.inputSerial, false, "Разпознавам…");
        return transcribe(key, item);
    }

    private void processAudioResult(AudioItem item, String raw, Throwable error) {
        if (item == null || !shouldProcessAudioResult(item)) return;
        if (error != null) {
            // Newer speech/pause cancellation is expected and fails freshness above.
            if (shouldSurfaceAudioResult(item)) {
                listener.onStatus(item.sessionSerial, item.inputSerial, false, "AI връзката прекъсна");
            }
            return;
        }
        if (raw == null) return;
        if (TranscriptQualityPolicy.isLowQuality(raw)) {
            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
            return;
        }

        long now = System.currentTimeMillis();
        String focus;
        synchronized (this) {
            // Network STT may finish out of order. OrderedParallelExecutor serializes
            // these commits in submission order before conversation state is mutated.
            if (!isProcessableAudioResultLocked(item)) {
                focus = "";
            } else {
                String useful = removeRecentSelfEcho(raw, now);
                if (useful.isEmpty()) {
                    focus = "";
                } else {
                    boolean topicShift = isStrongTopicShift(useful);
                    prepareContextFor(useful, now);
                    focus = commitTranscript(useful, now);
                    if (!focus.isEmpty()) {
                        if (fileTurnSerial != item.inputSerial) {
                            fileTurnSerial = item.inputSerial;
                            fileTurnFocus = "";
                        }
                        fileTurnFocus = FileTurnReplyPolicy.appendFocus(
                                fileTurnFocus, focus, topicShift);
                        if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;
                    }
                }
            }
        }
        if (focus.isEmpty()) {
            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
            return;
        }
        if (!shouldSurfaceAudioResult(item)) {
            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
            return;
        }
        listener.onTranscript(item.sessionSerial, item.inputSerial, focus);
        listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
    }

'''
s = s[:start] + replacement + s[end:]
s = one(
    s,
    "        listener.onFileTurnComplete(\n"
    "                end.sessionSerial, end.inputSerial, turnFocus, mainReplyQueued);",
    "        long drainLatencyMs = Math.max(0L, now - end.createdAtMs);\n"
    "        listener.onFileTurnComplete(\n"
    "                end.sessionSerial, end.inputSerial, turnFocus, mainReplyQueued, drainLatencyMs);",
    "drain callback",
)
p.write_text(s)

p = Path("app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java")
s = p.read_text()
s = one(
    s,
    "    private long lastSemanticLatencyMs = -1L;",
    "    private long lastSemanticLatencyMs = -1L;\n"
    "    private long lastFileDrainLatencyMs = -1L;",
    "drain metric field",
)
s = one(
    s,
    "    public void onFileTurnComplete(\n"
    "            long sessionSerial, long inputSerial, String turnFocus, boolean mainReplyQueued) {\n"
    "        getMainExecutor().execute(() -> handleFileTurnComplete(\n"
    "                sessionSerial, inputSerial, turnFocus, mainReplyQueued));\n"
    "    }\n\n"
    "    private synchronized void handleFileTurnComplete(\n"
    "            long sessionSerial, long inputSerial, String turnFocus, boolean mainReplyQueued) {",
    "    public void onFileTurnComplete(\n"
    "            long sessionSerial, long inputSerial, String turnFocus, boolean mainReplyQueued,\n"
    "            long drainLatencyMs) {\n"
    "        getMainExecutor().execute(() -> handleFileTurnComplete(\n"
    "                sessionSerial, inputSerial, turnFocus, mainReplyQueued, drainLatencyMs));\n"
    "    }\n\n"
    "    private synchronized void handleFileTurnComplete(\n"
    "            long sessionSerial, long inputSerial, String turnFocus, boolean mainReplyQueued,\n"
    "            long drainLatencyMs) {",
    "service turn callback",
)
s = one(
    s,
    "        if (engine == null || !engine.isRunning()) return;\n\n"
    "        if (!FileTurnReplyPolicy.shouldUseSemanticFallback(mainReplyQueued, turnFocus)) {",
    "        if (engine == null || !engine.isRunning()) return;\n"
    "        lastFileDrainLatencyMs = Math.max(0L, drainLatencyMs);\n"
    "        renderDebug();\n\n"
    "        if (!FileTurnReplyPolicy.shouldUseSemanticFallback(mainReplyQueued, turnFocus)) {",
    "record drain metric",
)
s = one(
    s,
    "                + \" • sem \" + latencyLabel(lastSemanticLatencyMs);",
    "                + \" • sem \" + latencyLabel(lastSemanticLatencyMs)\n"
    "                + \" • file-drain \" + latencyLabel(lastFileDrainLatencyMs);",
    "debug metric",
)
s = one(
    s,
    "    private void resetLatencyMetrics() {\n"
    "        lastVoiceEndAtMs = 0L;\n"
    "        lastFinalTranscriptLatencyMs = -1L;\n"
    "        lastFirstReplyLatencyMs = -1L;\n"
    "        lastStylesLatencyMs = -1L;\n"
    "        thinkingStartedAtMs = 0L;\n"
    "        lastSemanticLatencyMs = -1L;\n"
    "    }",
    "    private void resetLatencyMetrics() {\n"
    "        lastVoiceEndAtMs = 0L;\n"
    "        lastFinalTranscriptLatencyMs = -1L;\n"
    "        lastFirstReplyLatencyMs = -1L;\n"
    "        lastStylesLatencyMs = -1L;\n"
    "        thinkingStartedAtMs = 0L;\n"
    "        lastSemanticLatencyMs = -1L;\n"
    "        lastFileDrainLatencyMs = -1L;\n"
    "    }",
    "reset metric",
)
p.write_text(s)

p = Path("app/build.gradle.kts")
s = p.read_text()
s = one(s, "versionCode = 61", "versionCode = 62", "version code")
s = one(
    s,
    'versionName = "0.60.0-final-turn-replies"',
    'versionName = "0.61.0-parallel-file-stt"',
    "version name",
)
p.write_text(s)
