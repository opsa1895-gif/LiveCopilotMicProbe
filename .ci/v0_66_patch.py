from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"missing pattern in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


def replace_all(path, old, new, min_count=1):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count < min_count:
        raise SystemExit(f"expected >= {min_count} occurrences in {path}, got {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new))

build = "app/build.gradle.kts"
replace_once(build, 'versionCode = 66\n        versionName = "0.65.0-semantic-lifecycle-retry"',
             'versionCode = 67\n        versionName = "0.66.0-degraded-recovery-state"')

rt = "app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java"
replace_once(rt, '        void onState(String state);',
             '        void onState(long stateSerial, String state);')
replace_once(rt, '    private long speechEpoch;\n    private long committedSpeechEpoch;',
             '    private long speechEpoch;\n    private long committedSpeechEpoch;\n    private long stateSerial;')
replace_all(rt, 'listener.onState(', 'emitState(', min_count=8)
replace_once(rt, '                emitState("fallback_failed");',
             '                emitStateUnlessReady("fallback_failed");')
anchor = '''    private synchronized void markConnectionStable(WebSocket ws, long stableGeneration) {
'''
helpers = '''    private void emitState(String state) {
        long serial;
        synchronized (this) {
            serial = ++stateSerial;
        }
        listener.onState(serial, state);
    }

    private void emitStateUnlessReady(String state) {
        long serial;
        synchronized (this) {
            if (closed || (ready && socket != null)) return;
            serial = ++stateSerial;
        }
        listener.onState(serial, state);
    }

'''
replace_once(rt, anchor, helpers + anchor)

client = "app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java"
replace_once(client,
'''        void onFileTurnComplete(long sessionSerial, long inputSerial, String turnFocus,
                                boolean mainReplyQueued, long drainLatencyMs,
                                int submittedChunks, int failedChunks, boolean lastChunkFailed,
                                long nextDeadlineMs);''',
'''        void onFileTurnComplete(long sessionSerial, long inputSerial, String turnFocus,
                                boolean mainReplyQueued, long drainLatencyMs,
                                int submittedChunks, int failedChunks, boolean lastChunkFailed,
                                long nextDeadlineMs, int timeoutDrops, int networkDrops,
                                int qualityDrops, int otherDrops, int degradedTurnStreak,
                                long retryCooldownRemainingMs);''')
replace_once(client,
'''        final int chunkIndex;
        private volatile HttpURLConnection activeConnection;''',
'''        final int chunkIndex;
        final boolean allowRetry;
        private volatile HttpURLConnection activeConnection;''')
replace_once(client,
'''        AudioItem(short[] samples, int sampleRate, long sessionSerial, long inputSerial, int chunkIndex) {
            this.samples = samples;
            this.sampleRate = sampleRate;
            this.createdAtMs = System.currentTimeMillis();
            this.sessionSerial = sessionSerial;
            this.inputSerial = inputSerial;
            this.chunkIndex = chunkIndex;
        }''',
'''        AudioItem(short[] samples, int sampleRate, long sessionSerial, long inputSerial,
                  int chunkIndex, boolean allowRetry) {
            this.samples = samples;
            this.sampleRate = sampleRate;
            this.createdAtMs = System.currentTimeMillis();
            this.sessionSerial = sessionSerial;
            this.inputSerial = inputSerial;
            this.chunkIndex = chunkIndex;
            this.allowRetry = allowRetry;
        }''')
replace_once(client,
'''    private int fileTurnFailedChunks;
    private int fileTurnLastFailedChunkIndex;
    private long lastReplyAtMs;''',
'''    private int fileTurnFailedChunks;
    private int fileTurnLastFailedChunkIndex;
    private int fileTurnUsableChunks;
    private int fileTurnTimeoutDrops;
    private int fileTurnNetworkDrops;
    private int fileTurnQualityDrops;
    private int fileTurnOtherDrops;
    private int fileSttDegradedTurnStreak;
    private long fileSttRetryCooldownUntilMs;
    private long lastReplyAtMs;''')
replace_once(client,
'''        fileSttCommitDeadlineMs = AdaptiveFileSttDeadlinePolicy.initialDeadlineMs();
        fileTurnHadSttTimeout = false;''',
'''        fileSttCommitDeadlineMs = AdaptiveFileSttDeadlinePolicy.initialDeadlineMs();
        fileSttRetryCooldownUntilMs = 0L;
        fileSttDegradedTurnStreak = 0;
        fileTurnHadSttTimeout = false;''')
replace_all(client,
'''        fileTurnSubmittedChunks = 0;
        fileTurnFailedChunks = 0;
        fileTurnLastFailedChunkIndex = 0;''',
'''        fileTurnSubmittedChunks = 0;
        fileTurnFailedChunks = 0;
        fileTurnLastFailedChunkIndex = 0;
        fileTurnUsableChunks = 0;
        fileTurnTimeoutDrops = 0;
        fileTurnNetworkDrops = 0;
        fileTurnQualityDrops = 0;
        fileTurnOtherDrops = 0;''',
min_count=5)
replace_once(client,
'''        int chunkIndex = ++fileTurnSubmittedChunks;
        AudioItem item = new AudioItem(
                samples.clone(), sampleRate, sessionSerial, inputSerial, chunkIndex);''',
'''        int chunkIndex = ++fileTurnSubmittedChunks;
        boolean allowRetry = FileSttRecoveryPolicy.allowRetry(
                System.currentTimeMillis(), fileSttRetryCooldownUntilMs);
        AudioItem item = new AudioItem(
                samples.clone(), sampleRate, sessionSerial, inputSerial, chunkIndex, allowRetry);''')
replace_once(client,
'''                    if (error instanceof java.util.concurrent.TimeoutException) {
                        // Drop only the overdue chunk. Later same-turn chunks can
                        // still commit in order and feed the final turn reply.
                        item.cancel();
                        noteFileSttFailure(item, true);
                        listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
                        return;
                    }
                    if (error != null || raw == null) noteFileSttFailure(item, false);
                    processAudioResult(item, raw, error);''',
'''                    if (error instanceof java.util.concurrent.TimeoutException) {
                        // Drop only the overdue chunk. Later same-turn chunks can
                        // still commit in order and feed the final turn reply.
                        item.cancel();
                        noteFileSttFailure(item, FileSttRecoveryPolicy.DropReason.TIMEOUT);
                        listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
                        return;
                    }
                    processAudioResult(item, raw, error);''')
old_failure = '''    private synchronized void noteFileSttFailure(AudioItem item, boolean timedOut) {
        if (item == null || closed || !item.markFailureOnce()) return;
        if (item.sessionSerial != sessionSerial || item.inputSerial != latestInputSerial) return;
        if (item.chunkIndex <= 0 || item.chunkIndex > fileTurnSubmittedChunks) return;
        fileTurnFailedChunks = Math.min(fileTurnSubmittedChunks, fileTurnFailedChunks + 1);
        fileTurnLastFailedChunkIndex = Math.max(fileTurnLastFailedChunkIndex, item.chunkIndex);
        if (timedOut) fileTurnHadSttTimeout = true;
    }
'''
new_failure = '''    private synchronized void noteFileSttFailure(
            AudioItem item, FileSttRecoveryPolicy.DropReason reason) {
        if (item == null || closed || !item.markFailureOnce()) return;
        if (item.sessionSerial != sessionSerial || item.inputSerial != latestInputSerial) return;
        if (item.chunkIndex <= 0 || item.chunkIndex > fileTurnSubmittedChunks) return;
        fileTurnFailedChunks = Math.min(fileTurnSubmittedChunks, fileTurnFailedChunks + 1);
        fileTurnLastFailedChunkIndex = Math.max(fileTurnLastFailedChunkIndex, item.chunkIndex);
        FileSttRecoveryPolicy.DropReason safeReason = reason == null
                ? FileSttRecoveryPolicy.DropReason.OTHER : reason;
        switch (safeReason) {
            case TIMEOUT:
                fileTurnTimeoutDrops++;
                fileTurnHadSttTimeout = true;
                break;
            case NETWORK:
                fileTurnNetworkDrops++;
                break;
            case QUALITY:
                fileTurnQualityDrops++;
                break;
            default:
                fileTurnOtherDrops++;
                break;
        }
    }

    private synchronized void noteFileSttSuccess(AudioItem item) {
        if (item == null || closed) return;
        if (item.sessionSerial != sessionSerial || item.inputSerial != latestInputSerial) return;
        if (item.chunkIndex <= 0 || item.chunkIndex > fileTurnSubmittedChunks) return;
        fileTurnUsableChunks = Math.min(fileTurnSubmittedChunks, fileTurnUsableChunks + 1);
    }
'''
replace_once(client, old_failure, new_failure)
replace_once(client,
'''        if (error != null) {
            // Newer speech/pause cancellation is expected and fails freshness above.
            if (shouldSurfaceAudioResult(item)) {
                listener.onStatus(item.sessionSerial, item.inputSerial, false, "AI връзката прекъсна");
            }
            return;
        }
        if (raw == null) return;
        if (TranscriptQualityPolicy.isLowQuality(raw)) {
            // A current speech chunk that produced unusable STT still lowers turn
            // coverage. This prevents a misleading 100% confidence score when the
            // network succeeded but recognition did not yield usable text.
            noteFileSttFailure(item, false);
            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
            return;
        }

        long now = System.currentTimeMillis();''',
'''        if (error != null) {
            noteFileSttFailure(item, FileSttRecoveryPolicy.isNetworkLike(error)
                    ? FileSttRecoveryPolicy.DropReason.NETWORK
                    : FileSttRecoveryPolicy.DropReason.OTHER);
            // Newer speech/pause cancellation is expected and fails freshness above.
            if (shouldSurfaceAudioResult(item)) {
                listener.onStatus(item.sessionSerial, item.inputSerial, false, "AI връзката прекъсна");
            }
            return;
        }
        if (raw == null) {
            noteFileSttFailure(item, FileSttRecoveryPolicy.DropReason.OTHER);
            return;
        }
        if (TranscriptQualityPolicy.isLowQuality(raw)) {
            // A current speech chunk that produced unusable STT still lowers turn
            // coverage. This prevents a misleading 100% confidence score when the
            // network succeeded but recognition did not yield usable text.
            noteFileSttFailure(item, FileSttRecoveryPolicy.DropReason.QUALITY);
            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");
            return;
        }
        noteFileSttSuccess(item);

        long now = System.currentTimeMillis();''')
replace_once(client,
'''        boolean lastChunkFailed;
        long nextDeadlineMs;''',
'''        boolean lastChunkFailed;
        long nextDeadlineMs;
        int timeoutDrops;
        int networkDrops;
        int qualityDrops;
        int otherDrops;
        int degradedTurnStreak;
        long retryCooldownRemainingMs;''')
replace_once(client,
'''            submittedChunks = fileTurnSubmittedChunks;
            failedChunks = fileTurnFailedChunks;
            lastChunkFailed = submittedChunks > 0
                    && fileTurnLastFailedChunkIndex == submittedChunks;

            turnFocus = fileTurnFocus;''',
'''            submittedChunks = fileTurnSubmittedChunks;
            failedChunks = fileTurnFailedChunks;
            lastChunkFailed = submittedChunks > 0
                    && fileTurnLastFailedChunkIndex == submittedChunks;
            timeoutDrops = fileTurnTimeoutDrops;
            networkDrops = fileTurnNetworkDrops;
            qualityDrops = fileTurnQualityDrops;
            otherDrops = fileTurnOtherDrops;

            boolean degraded = FileSttRecoveryPolicy.shouldDegradeTurn(
                    submittedChunks, fileTurnUsableChunks, timeoutDrops, networkDrops);
            if (degraded) {
                fileSttDegradedTurnStreak++;
                long cooldownMs = FileSttRecoveryPolicy.cooldownMsForStreak(
                        fileSttDegradedTurnStreak);
                if (cooldownMs > 0L) {
                    fileSttRetryCooldownUntilMs = Math.max(
                            fileSttRetryCooldownUntilMs, now + cooldownMs);
                }
            } else if (fileTurnUsableChunks > 0) {
                fileSttDegradedTurnStreak = 0;
                fileSttRetryCooldownUntilMs = 0L;
            }
            degradedTurnStreak = fileSttDegradedTurnStreak;
            retryCooldownRemainingMs = Math.max(0L, fileSttRetryCooldownUntilMs - now);

            turnFocus = fileTurnFocus;''')
replace_once(client,
'''                end.sessionSerial, end.inputSerial, turnFocus, mainReplyQueued, drainLatencyMs,
                submittedChunks, failedChunks, lastChunkFailed, nextDeadlineMs);''',
'''                end.sessionSerial, end.inputSerial, turnFocus, mainReplyQueued, drainLatencyMs,
                submittedChunks, failedChunks, lastChunkFailed, nextDeadlineMs,
                timeoutDrops, networkDrops, qualityDrops, otherDrops,
                degradedTurnStreak, retryCooldownRemainingMs);''')
replace_once(client,
'''        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {''',
'''        Exception last = null;
        int maxAttempts = item.allowRetry ? 2 : 1;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {''')
replace_once(client,
'''            if (attempt == 0) {
                if (!shouldProcessAudioResult(item)) {''',
'''            if (attempt + 1 < maxAttempts) {
                if (!shouldProcessAudioResult(item)) {''')

service = "app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java"
replace_once(service,
'''    private volatile long realtimeSpeechEpoch = -1L;
    private volatile long fileSpeechEpoch = -1L;''',
'''    private volatile long realtimeSpeechEpoch = -1L;
    private volatile long fileSpeechEpoch = -1L;
    private long realtimeStateSerial;''')
replace_once(service,
'''    private long lastFileSttDeadlineMs = -1L;
    private String lastSemanticTerminal = "";''',
'''    private long lastFileSttDeadlineMs = -1L;
    private int lastFileTimeoutDrops;
    private int lastFileNetworkDrops;
    private int lastFileQualityDrops;
    private int lastFileOtherDrops;
    private int lastFileDegradedStreak;
    private long lastFileRetryCooldownMs;
    private String lastSemanticTerminal = "";''')
replace_once(service,
'''            @Override
            public void onState(String state) {
                getMainExecutor().execute(() -> {
                    realtimeState = state == null ? "?" : state;
                    if (!"ready".equals(realtimeState)) realtimePartial = "";
                    renderDebug();
                });
            }''',
'''            @Override
            public void onState(long stateSerial, String state) {
                getMainExecutor().execute(() -> handleRealtimeState(stateSerial, state));
            }''')
replace_once(service,
'''    private synchronized void handleRealtimePartial(long speechEpoch, String partial) {''',
'''    private synchronized void handleRealtimeState(long stateSerial, String state) {
        if (!RealtimeStateFreshnessPolicy.shouldAccept(stateSerial, realtimeStateSerial)) return;
        realtimeStateSerial = stateSerial;
        realtimeState = state == null ? "?" : state;
        if (!"ready".equals(realtimeState)) realtimePartial = "";
        renderDebug();
    }

    private synchronized void handleRealtimePartial(long speechEpoch, String partial) {''')
replace_once(service,
'''            long drainLatencyMs, int submittedChunks, int failedChunks,
            boolean lastChunkFailed, long nextDeadlineMs) {
        getMainExecutor().execute(() -> handleFileTurnComplete(
                sessionSerial, inputSerial, turnFocus, mainReplyQueued, drainLatencyMs,
                submittedChunks, failedChunks, lastChunkFailed, nextDeadlineMs));''',
'''            long drainLatencyMs, int submittedChunks, int failedChunks,
            boolean lastChunkFailed, long nextDeadlineMs, int timeoutDrops, int networkDrops,
            int qualityDrops, int otherDrops, int degradedTurnStreak,
            long retryCooldownRemainingMs) {
        getMainExecutor().execute(() -> handleFileTurnComplete(
                sessionSerial, inputSerial, turnFocus, mainReplyQueued, drainLatencyMs,
                submittedChunks, failedChunks, lastChunkFailed, nextDeadlineMs,
                timeoutDrops, networkDrops, qualityDrops, otherDrops,
                degradedTurnStreak, retryCooldownRemainingMs));''')
replace_once(service,
'''            long drainLatencyMs, int submittedChunks, int failedChunks,
            boolean lastChunkFailed, long nextDeadlineMs) {''',
'''            long drainLatencyMs, int submittedChunks, int failedChunks,
            boolean lastChunkFailed, long nextDeadlineMs, int timeoutDrops, int networkDrops,
            int qualityDrops, int otherDrops, int degradedTurnStreak,
            long retryCooldownRemainingMs) {''')
replace_once(service,
'''        lastFileTailMissing = lastChunkFailed;
        lastFileSttDeadlineMs = Math.max(0L, nextDeadlineMs);
        renderDebug();''',
'''        lastFileTailMissing = lastChunkFailed;
        lastFileSttDeadlineMs = Math.max(0L, nextDeadlineMs);
        lastFileTimeoutDrops = Math.max(0, timeoutDrops);
        lastFileNetworkDrops = Math.max(0, networkDrops);
        lastFileQualityDrops = Math.max(0, qualityDrops);
        lastFileOtherDrops = Math.max(0, otherDrops);
        lastFileDegradedStreak = Math.max(0, degradedTurnStreak);
        lastFileRetryCooldownMs = Math.max(0L, retryCooldownRemainingMs);
        if (submittedChunks > 0 && failedChunks >= submittedChunks
                && lastFileTimeoutDrops + lastFileNetworkDrops > 0
                && lastFileRetryCooldownMs > 0L) {
            aiStatus = "Слушам • нестабилна връзка";
            renderStatus();
        }
        renderDebug();''')
replace_once(service,
'''        String rt = " • RT " + realtimeState;''',
'''        String rt = " • RT " + realtimeState
                + (realtimeStateSerial > 0L ? "@" + realtimeStateSerial : "");''')
replace_once(service,
'''                + (lastFileTailMissing ? " • tail-missing" : "")
                + " • budget " + latencyLabel(lastFileSttDeadlineMs);''',
'''                + (lastFileTailMissing ? " • tail-missing" : "")
                + " • drops t/n/q/o " + lastFileTimeoutDrops + "/"
                + lastFileNetworkDrops + "/" + lastFileQualityDrops + "/" + lastFileOtherDrops
                + " • budget " + latencyLabel(lastFileSttDeadlineMs)
                + (lastFileDegradedStreak > 0 ? " • degraded×" + lastFileDegradedStreak : "")
                + (lastFileRetryCooldownMs > 0L
                ? " • retry-cd " + latencyLabel(lastFileRetryCooldownMs) : "");''')
replace_once(service,
'''        lastFileTailMissing = false;
        lastFileSttDeadlineMs = -1L;
        lastSemanticTerminal = "";''',
'''        lastFileTailMissing = false;
        lastFileSttDeadlineMs = -1L;
        lastFileTimeoutDrops = 0;
        lastFileNetworkDrops = 0;
        lastFileQualityDrops = 0;
        lastFileOtherDrops = 0;
        lastFileDegradedStreak = 0;
        lastFileRetryCooldownMs = 0L;
        lastSemanticTerminal = "";''')

print("v0.66 patch applied")
