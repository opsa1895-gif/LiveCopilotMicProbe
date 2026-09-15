from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"missing pattern in {path}: {old[:140]!r}")
    p.write_text(text.replace(old, new, 1))

build = "app/build.gradle.kts"
replace_once(
    build,
    'versionCode = 67\n        versionName = "0.66.0-degraded-recovery-state"',
    'versionCode = 68\n        versionName = "0.67.0-realtime-route-hysteresis"')

rt = "app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java"
replace_once(rt,
'''final class RealtimeTranscriptionClient {
    interface Listener {''',
'''final class RealtimeTranscriptionClient {
    enum TranscriptSource {
        REALTIME,
        FILE_RECOVERY
    }

    interface Listener {''')
replace_once(rt,
'''        void onFinal(long turnSerial, long speechEpoch, String transcript);''',
'''        void onFinal(long turnSerial, long speechEpoch, TranscriptSource source, String transcript);''')
replace_once(rt,
'''    private long committedSpeechEpoch;
    private long stateSerial;
    private long latestRecoveryId;''',
'''    private long committedSpeechEpoch;
    private long stateSerial;
    private long readyAtMs;
    private int unstableReadyFailureStreak;
    private long routingBlockedUntilMs;
    private long latestRecoveryId;''')
replace_once(rt,
'''        sentSamples = 0;
        partial.setLength(0);
        clearBackupsLocked();''',
'''        sentSamples = 0;
        partial.setLength(0);
        readyAtMs = 0L;
        unstableReadyFailureStreak = 0;
        routingBlockedUntilMs = 0L;
        clearBackupsLocked();''')
replace_once(rt,
'''    synchronized boolean beginTurn(int sourceSampleRate) {
        if (!ready || socket == null || turnActive || awaitingCompletion || sourceSampleRate <= 0) return false;
        turnActive = true;''',
'''    synchronized boolean beginTurn(int sourceSampleRate) {
        long now = System.currentTimeMillis();
        if (!RealtimeRoutingPolicy.shouldUseRealtime(ready, socket != null, now, routingBlockedUntilMs)
                || turnActive || awaitingCompletion || sourceSampleRate <= 0) return false;
        turnActive = true;''')
replace_once(rt,
'''        if (!sent) {
            socket = null;
            turnActive = false;
            ready = false;''',
'''        if (!sent) {
            noteReadyFailureLocked();
            socket = null;
            turnActive = false;
            ready = false;''')
replace_once(rt,
'''            if (clearFailed) {
                failedSocket = socket;
                socket = null;
                ready = false;''',
'''            if (clearFailed) {
                failedSocket = socket;
                noteReadyFailureLocked();
                socket = null;
                ready = false;''')
replace_once(rt,
'''        if (!sent) {
            socket = null;
            sentSamples = 0;
            ready = false;''',
'''        if (!sent) {
            noteReadyFailureLocked();
            socket = null;
            sentSamples = 0;
            ready = false;''')
replace_once(rt,
'''    synchronized boolean isReady() {
        return ready && socket != null;
    }
''',
'''    synchronized boolean isReady() {
        return ready && socket != null;
    }

    synchronized long routingBlockRemainingMs() {
        if (routingBlockedUntilMs <= 0L) return 0L;
        return Math.max(0L, routingBlockedUntilMs - System.currentTimeMillis());
    }

    synchronized int unstableReadyFailureStreak() {
        return Math.max(0, unstableReadyFailureStreak);
    }
''')
replace_once(rt,
'''        reconnectScheduled = false;
        ready = true;
        long stableGeneration = generation;''',
'''        reconnectScheduled = false;
        ready = true;
        readyAtMs = System.currentTimeMillis();
        long stableGeneration = generation;''')
replace_once(rt,
'''                listener.onFinal(turn, completedSpeechEpoch, transcript);''',
'''                listener.onFinal(turn, completedSpeechEpoch, TranscriptSource.REALTIME, transcript);''')
replace_once(rt,
'''                failedSocket = socket;
                socket = null;
                ready = false;
                turnActive = false;
                awaitingCompletion = false;''',
'''                failedSocket = socket;
                noteReadyFailureLocked();
                socket = null;
                ready = false;
                turnActive = false;
                awaitingCompletion = false;''')
replace_once(rt,
'''            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
        }
        emitState("fallback");
        if (recoveryTurn > 0L) recoverCommittedTurn(recoveryTurn, recoveryGeneration, "closed");''',
'''            noteReadyFailureLocked();
            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
        }
        emitState("fallback");
        if (recoveryTurn > 0L) recoverCommittedTurn(recoveryTurn, recoveryGeneration, "closed");''')
replace_once(rt,
'''            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
        }
        emitState("fallback");
        if (recoveryTurn > 0L) recoverCommittedTurn(recoveryTurn, recoveryGeneration, "failure");''',
'''            noteReadyFailureLocked();
            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
        }
        emitState("fallback");
        if (recoveryTurn > 0L) recoverCommittedTurn(recoveryTurn, recoveryGeneration, "failure");''')
replace_once(rt,
'''            awaitingCompletion = false;
            ready = false;
            turnActive = false;''',
'''            awaitingCompletion = false;
            noteReadyFailureLocked();
            ready = false;
            turnActive = false;''')
replace_once(rt,
'''                listener.onFinal(turn, recoverySpeechEpoch, transcript);''',
'''                listener.onFinal(turn, recoverySpeechEpoch, TranscriptSource.FILE_RECOVERY, transcript);''')
replace_once(rt,
'''        synchronized (this) {
            if (ws != socket || closed || !wanted) return;
            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
        }
        emitState("fallback");
        try { ws.close(1011, "context_update_failed"); } catch (Throwable ignored) {}''',
'''        synchronized (this) {
            if (ws != socket || closed || !wanted) return;
            noteReadyFailureLocked();
            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
        }
        emitState("fallback");
        try { ws.close(1011, "context_update_failed"); } catch (Throwable ignored) {}''')
replace_once(rt,
'''    private synchronized void markConnectionStable(WebSocket ws, long stableGeneration) {
        if (closed || !wanted || generation != stableGeneration || socket != ws || !ready) return;
        reconnectAttempt = 0;
    }''',
'''    private void noteReadyFailureLocked() {
        long now = System.currentTimeMillis();
        if (RealtimeRoutingPolicy.isQuickFailure(
                readyAtMs, now, RealtimeReconnectPolicy.STABLE_RESET_MS)) {
            unstableReadyFailureStreak++;
            long blockMs = RealtimeRoutingPolicy.blockMsForUnstableStreak(
                    unstableReadyFailureStreak);
            if (blockMs > 0L) {
                routingBlockedUntilMs = Math.max(routingBlockedUntilMs, now + blockMs);
            }
        } else if (readyAtMs > 0L) {
            unstableReadyFailureStreak = 0;
            routingBlockedUntilMs = 0L;
        }
        readyAtMs = 0L;
    }

    private synchronized void markConnectionStable(WebSocket ws, long stableGeneration) {
        if (closed || !wanted || generation != stableGeneration || socket != ws || !ready) return;
        reconnectAttempt = 0;
        unstableReadyFailureStreak = 0;
        routingBlockedUntilMs = 0L;
    }''')

service = "app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java"
replace_once(service,
'''public class MicProbeAccessibilityService extends AccessibilityService implements MicProbeEngine.Listener, OpenAiCopilotClient.Listener {
    private static final String UI_PREFS = "live_copilot_ui";''',
'''public class MicProbeAccessibilityService extends AccessibilityService implements MicProbeEngine.Listener, OpenAiCopilotClient.Listener {
    private enum AcceptedSttSource {
        REALTIME("rt", true, true, true),
        REALTIME_FILE_RECOVERY("rt-file", false, true, true),
        FILE_PRIMARY("file", false, false, false);

        final String label;
        final boolean realtimeTransport;
        final boolean bridgeToFileContext;
        final boolean scheduleSemantic;

        AcceptedSttSource(String label, boolean realtimeTransport,
                          boolean bridgeToFileContext, boolean scheduleSemantic) {
            this.label = label;
            this.realtimeTransport = realtimeTransport;
            this.bridgeToFileContext = bridgeToFileContext;
            this.scheduleSemantic = scheduleSemantic;
        }
    }

    private static final String UI_PREFS = "live_copilot_ui";''')
replace_once(service,
'''    private boolean lastAcceptedFromRealtime;
    private long answerUpdatedAtMs;''',
'''    private boolean lastAcceptedFromRealtime;
    private String lastAcceptedSttSource = "—";
    private String lastTurnSttRoute = "—";
    private int acceptedRealtimeTranscripts;
    private int acceptedRealtimeRecoveryTranscripts;
    private int acceptedPrimaryFileTranscripts;
    private long answerUpdatedAtMs;''')
replace_once(service,
'''            public void onFinal(long turnSerial, long speechEpoch, String transcript) {
                getMainExecutor().execute(() -> handleRealtimeFinal(turnSerial, speechEpoch, transcript));
            }''',
'''            public void onFinal(long turnSerial, long speechEpoch,
                                RealtimeTranscriptionClient.TranscriptSource source,
                                String transcript) {
                getMainExecutor().execute(() ->
                        handleRealtimeFinal(turnSerial, speechEpoch, source, transcript));
            }''')
replace_once(service,
'''        realtimeTurnActive = realtimeTranscriber != null && realtimeTranscriber.beginTurn(sampleRate);
        // Only turns that actually entered Realtime need a contiguous emergency
        // backup. Pure file-fallback turns keep using the chunk/overlap path.
        realtimeBackupStreaming = realtimeTurnActive;''',
'''        realtimeTurnActive = realtimeTranscriber != null && realtimeTranscriber.beginTurn(sampleRate);
        long routeBlockMs = realtimeTranscriber == null
                ? 0L : realtimeTranscriber.routingBlockRemainingMs();
        lastTurnSttRoute = realtimeTurnActive
                ? "rt"
                : (routeBlockMs > 0L ? "file-hyst" : "file");
        // Only turns that actually entered Realtime need a contiguous emergency
        // backup. Pure file-fallback turns keep using the chunk/overlap path.
        realtimeBackupStreaming = realtimeTurnActive;''')
replace_once(service,
'''        acceptTranscript(transcript, false);''',
'''        acceptTranscript(transcript, AcceptedSttSource.FILE_PRIMARY);''')
replace_once(service,
'''    private synchronized void handleRealtimeFinal(long turnSerial, long speechEpoch, String transcript) {
        if (!RealtimeEventFreshnessPolicy.shouldAccept(speechEpoch, realtimeSpeechEpoch)) return;
        if (engine == null || !engine.isRunning()) return;
        if (turnSerial <= lastRealtimeTurnSerial) return;
        lastRealtimeTurnSerial = turnSerial;
        realtimeSpeechEpoch = -1L;
        realtimePartial = "";
        acceptTranscript(transcript, true);
    }

    private void acceptTranscript(String transcript, boolean fromRealtime) {
        String clean = transcript == null ? "" : transcript.replace('\\n', ' ').trim();''',
'''    private synchronized void handleRealtimeFinal(
            long turnSerial, long speechEpoch,
            RealtimeTranscriptionClient.TranscriptSource source, String transcript) {
        if (!RealtimeEventFreshnessPolicy.shouldAccept(speechEpoch, realtimeSpeechEpoch)) return;
        if (engine == null || !engine.isRunning()) return;
        if (turnSerial <= lastRealtimeTurnSerial) return;
        lastRealtimeTurnSerial = turnSerial;
        realtimeSpeechEpoch = -1L;
        realtimePartial = "";
        AcceptedSttSource acceptedSource = source == RealtimeTranscriptionClient.TranscriptSource.FILE_RECOVERY
                ? AcceptedSttSource.REALTIME_FILE_RECOVERY
                : AcceptedSttSource.REALTIME;
        acceptTranscript(transcript, acceptedSource);
    }

    private void acceptTranscript(String transcript, AcceptedSttSource source) {
        if (source == null) source = AcceptedSttSource.FILE_PRIMARY;
        boolean fromRealtime = source.realtimeTransport;
        String clean = transcript == null ? "" : transcript.replace('\\n', ' ').trim();''')
replace_once(service,
'''        if (fromRealtime && !useful.isEmpty() && aiClient != null) {''',
'''        if (source.bridgeToFileContext && !useful.isEmpty() && aiClient != null) {''')
replace_once(service,
'''        if (!useful.isEmpty()) {
            semanticEpoch++;''',
'''        if (!useful.isEmpty()) {
            recordAcceptedSttSource(source);
            semanticEpoch++;''')
replace_once(service,
'''        if (fromRealtime && !useful.isEmpty()) {
            scheduleSemanticFallbackIfNeeded();
        }
    }

    @Override
    public void onReplies''',
'''        if (source.scheduleSemantic && !useful.isEmpty()) {
            scheduleSemanticFallbackIfNeeded();
        }
    }

    private void recordAcceptedSttSource(AcceptedSttSource source) {
        lastAcceptedSttSource = source.label;
        switch (source) {
            case REALTIME:
                acceptedRealtimeTranscripts++;
                break;
            case REALTIME_FILE_RECOVERY:
                acceptedRealtimeRecoveryTranscripts++;
                break;
            default:
                acceptedPrimaryFileTranscripts++;
                break;
        }
    }

    @Override
    public void onReplies''')
replace_once(service,
'''        debugText.setMaxLines(6);''',
'''        debugText.setMaxLines(7);''')
replace_once(service,
'''        String rt = " • RT " + realtimeState
                + (realtimeStateSerial > 0L ? "@" + realtimeStateSerial : "");''',
'''        long routeBlockMs = realtimeTranscriber == null
                ? 0L : realtimeTranscriber.routingBlockRemainingMs();
        int unstableRt = realtimeTranscriber == null
                ? 0 : realtimeTranscriber.unstableReadyFailureStreak();
        String rt = " • RT " + realtimeState
                + (realtimeStateSerial > 0L ? "@" + realtimeStateSerial : "")
                + (unstableRt > 0 ? " • unstable×" + unstableRt : "")
                + (routeBlockMs > 0L ? " • route-cd " + latencyLabel(routeBlockMs) : "");''')
replace_once(service,
'''        String fileConfidence = lastFileCoveragePercent < 0 ? ""''',
'''        String sourceStats = "\\nSTT src " + lastAcceptedSttSource
                + " • rt " + acceptedRealtimeTranscripts
                + " • rt-file " + acceptedRealtimeRecoveryTranscripts
                + " • file " + acceptedPrimaryFileTranscripts
                + " • route " + lastTurnSttRoute;
        String fileConfidence = lastFileCoveragePercent < 0 ? ""''')
replace_once(service,
'''        debugText.setText(app + " • " + mic + rt + echo + heard + partial + latency
                + fileConfidence + semanticLifecycle);''',
'''        debugText.setText(app + " • " + mic + rt + echo + heard + partial + latency
                + sourceStats + fileConfidence + semanticLifecycle);''')
replace_once(service,
'''        lastFileRetryCooldownUntilMs = 0L;
        lastSemanticTerminal = "";''',
'''        lastFileRetryCooldownUntilMs = 0L;
        lastAcceptedSttSource = "—";
        lastTurnSttRoute = "—";
        acceptedRealtimeTranscripts = 0;
        acceptedRealtimeRecoveryTranscripts = 0;
        acceptedPrimaryFileTranscripts = 0;
        lastSemanticTerminal = "";''')

print("v0.67 source patch applied")
