package com.livecopilot.micprobe;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class RealtimeTranscriptionClient {
    enum TranscriptSource {
        REALTIME,
        FILE_RECOVERY
    }

    interface Listener {
        void onState(long stateSerial, String state);
        void onPartial(long speechEpoch, String partial);
        void onFinal(long turnSerial, long speechEpoch, TranscriptSource source, String transcript);
    }

    private static final String WS_URL = "wss://api.openai.com/v1/realtime?model=gpt-live-transcribe";
    private static final String FILE_STT_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final int REALTIME_SAMPLE_RATE = 24_000;
    private static final int MIN_COMMIT_SAMPLES = REALTIME_SAMPLE_RATE / 10; // 100 ms
    private static final int MAX_BACKUP_SAMPLES = 16_000 * 12; // bounded ~12 s at capture rate
    private static final int STT_CONTEXT_TURNS = 4;
    private static final long STT_CONTEXT_IDLE_RESET_MS = 45_000L;

    private final Context context;
    private final Listener listener;
    private final OkHttpClient httpClient;
    private final OkHttpClient fallbackHttpClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final LatestWinsExecutor fallbackExecutor = new LatestWinsExecutor(2);
    private final StreamingAudioPreprocessor streamingPreprocessor = new StreamingAudioPreprocessor();
    private final PcmTurnBuffer activeBackup = new PcmTurnBuffer(MAX_BACKUP_SAMPLES);
    private final PcmTurnBuffer pendingBackup = new PcmTurnBuffer(MAX_BACKUP_SAMPLES);
    private final SttContextWindow contextWindow = new SttContextWindow(STT_CONTEXT_TURNS, STT_CONTEXT_IDLE_RESET_MS);

    private WebSocket socket;
    private boolean wanted;
    private boolean closed;
    private boolean ready;
    private boolean turnActive;
    private boolean awaitingCompletion;
    private boolean reconnectScheduled;
    private int reconnectAttempt;
    private long serial;
    private long activeTurnSerial;
    private long committedTurnSerial;
    private long pendingBackupTurnSerial;
    private long generation;
    private long speechEpoch;
    private long committedSpeechEpoch;
    private long stateSerial;
    private long readyAtMs;
    private int unstableReadyFailureStreak;
    private int routingQualityPenalty;
    private int routingLatencyPenalty;
    private int goodRealtimeStreak;
    private long lastRoutingSignalAtMs;
    private long realtimeRouteLatencyEstimateMs = -1L;
    private long realtimeRouteLatencyJitterMs = -1L;
    private int realtimeRouteLatencySamples;
    private long realtimeRouteLatencySampleAtMs;
    private int realtimeRouteLatencyOutlierDirection;
    private int realtimeRouteLatencyOutlierStreak;
    private long fileRouteLatencyEstimateMs = -1L;
    private long fileRouteLatencyJitterMs = -1L;
    private int fileRouteLatencySamples;
    private long fileRouteLatencySampleAtMs;
    private int fileRouteLatencyOutlierDirection;
    private int fileRouteLatencyOutlierStreak;
    private String lastRouteDecisionLabel = "file";
    private long transportBlockedUntilMs;
    private long outcomeBlockedUntilMs;
    private long latestRecoveryId;
    private int activeBackupSampleRate = 16_000;
    private int pendingBackupSampleRate = 16_000;
    private int sentSamples;
    private StringBuilder partial = new StringBuilder();

    RealtimeTranscriptionClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(40, TimeUnit.SECONDS)
                .build();
        this.fallbackHttpClient = this.httpClient.newBuilder()
                .callTimeout(12, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(9, TimeUnit.SECONDS)
                .writeTimeout(9, TimeUnit.SECONDS)
                .build();
    }

    synchronized void start() {
        if (closed) return;
        if (!wanted) generation++;
        wanted = true;
        if (socket == null) connectLocked();
    }

    synchronized void stop() {
        generation++;
        wanted = false;
        ready = false;
        turnActive = false;
        awaitingCompletion = false;
        sentSamples = 0;
        partial.setLength(0);
        readyAtMs = 0L;
        unstableReadyFailureStreak = 0;
        routingQualityPenalty = 0;
        routingLatencyPenalty = 0;
        goodRealtimeStreak = 0;
        lastRoutingSignalAtMs = 0L;
        realtimeRouteLatencyEstimateMs = -1L;
        realtimeRouteLatencyJitterMs = -1L;
        realtimeRouteLatencySamples = 0;
        realtimeRouteLatencySampleAtMs = 0L;
        realtimeRouteLatencyOutlierDirection = 0;
        realtimeRouteLatencyOutlierStreak = 0;
        fileRouteLatencyEstimateMs = -1L;
        fileRouteLatencyJitterMs = -1L;
        fileRouteLatencySamples = 0;
        fileRouteLatencySampleAtMs = 0L;
        fileRouteLatencyOutlierDirection = 0;
        fileRouteLatencyOutlierStreak = 0;
        lastRouteDecisionLabel = "file";
        transportBlockedUntilMs = 0L;
        outcomeBlockedUntilMs = 0L;
        clearBackupsLocked();
        WebSocket old = socket;
        socket = null;
        if (old != null) {
            try { old.close(1000, "pause"); } catch (Throwable ignored) {}
        }
        emitState("off");
    }

    synchronized long noteNewSpeech() {
        speechEpoch++;
        latestRecoveryId++;
        return speechEpoch;
    }

    synchronized boolean beginTurn(int sourceSampleRate) {
        long now = System.currentTimeMillis();
        decayRoutingPenaltiesLocked(now);
        long blockedUntilMs = RealtimeRoutingPolicy.effectiveBlockedUntil(
                transportBlockedUntilMs, outcomeBlockedUntilMs);
        lastRouteDecisionLabel = "file";
        if (!RealtimeRoutingPolicy.shouldUseRealtime(ready, socket != null, now, blockedUntilMs)) {
            if (ready && socket != null && blockedUntilMs > now) {
                lastRouteDecisionLabel = "file-hyst";
            }
            return false;
        }
        if (turnActive || awaitingCompletion || sourceSampleRate <= 0) return false;
        if (RealtimeRoutingPolicy.shouldPreferFileForLatency(
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                realtimeRouteLatencySamples, realtimeRouteLatencySampleAtMs,
                fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs, now)) {
            lastRouteDecisionLabel = "file-perf";
            return false;
        }
        lastRouteDecisionLabel = "rt";
        turnActive = true;
        activeTurnSerial = ++serial;
        sentSamples = 0;
        partial.setLength(0);
        activeBackup.clear();
        activeBackupSampleRate = sourceSampleRate;
        streamingPreprocessor.reset(sourceSampleRate);
        return true;
    }

    synchronized boolean append(short[] pcm16, int sourceSampleRate) {
        if (!turnActive || !ready || socket == null || pcm16 == null || pcm16.length == 0) return false;

        if (sourceSampleRate == activeBackupSampleRate) activeBackup.append(pcm16);

        short[] cleaned = streamingPreprocessor.process(pcm16, sourceSampleRate);
        short[] realtime = PcmResampler.resample(cleaned, sourceSampleRate, REALTIME_SAMPLE_RATE);
        if (realtime.length == 0) return true;

        JSONObject event = new JSONObject();
        try {
            event.put("type", "input_audio_buffer.append");
            event.put("audio", Base64.encodeToString(toLittleEndian(realtime), Base64.NO_WRAP));
        } catch (Throwable ignored) {
            return false;
        }

        WebSocket failedSocket = socket;
        boolean sent;
        try { sent = failedSocket.send(event.toString()); }
        catch (Throwable t) { sent = false; }
        if (!sent) {
            noteReadyFailureLocked();
            socket = null;
            turnActive = false;
            ready = false;
            sentSamples = 0;
            activeBackup.clear();
            emitState("fallback");
            try { failedSocket.close(1011, "append_failed"); } catch (Throwable ignored) {}
            scheduleReconnect();
            return false;
        }
        sentSamples += realtime.length;
        return true;
    }

    synchronized boolean commitTurn() {
        if (!turnActive) return false;
        turnActive = false;
        if (!ready || socket == null || sentSamples < MIN_COMMIT_SAMPLES) {
            WebSocket failedSocket = null;
            boolean clearFailed = ready && socket != null && !sendClearLocked();
            sentSamples = 0;
            activeBackup.clear();
            partial.setLength(0);
            listener.onPartial(speechEpoch, "");
            if (clearFailed) {
                failedSocket = socket;
                noteReadyFailureLocked();
                socket = null;
                ready = false;
                awaitingCompletion = false;
                emitState("fallback");
                try { failedSocket.close(1011, "clear_failed"); } catch (Throwable ignored) {}
                scheduleReconnect();
            }
            return false;
        }

        JSONObject event = new JSONObject();
        try { event.put("type", "input_audio_buffer.commit"); }
        catch (Throwable ignored) { return false; }

        WebSocket failedSocket = socket;
        boolean sent;
        try { sent = failedSocket.send(event.toString()); }
        catch (Throwable t) { sent = false; }
        if (!sent) {
            noteReadyFailureLocked();
            socket = null;
            sentSamples = 0;
            ready = false;
            awaitingCompletion = false;
            activeBackup.clear();
            emitState("fallback");
            try { failedSocket.close(1011, "commit_failed"); } catch (Throwable ignored) {}
            scheduleReconnect();
            return false;
        }

        committedTurnSerial = activeTurnSerial;
        committedSpeechEpoch = speechEpoch;
        awaitingCompletion = true;
        sentSamples = 0;

        pendingBackup.clear();
        pendingBackup.append(activeBackup.copy());
        pendingBackupSampleRate = activeBackupSampleRate;
        pendingBackupTurnSerial = committedTurnSerial;
        activeBackup.clear();

        long timeoutTurn = committedTurnSerial;
        long timeoutGeneration = generation;
        scheduler.schedule(() -> handleCommitTimeout(timeoutTurn, timeoutGeneration, false),
                RealtimeCommitPolicy.SOFT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    synchronized boolean isReady() {
        return ready && socket != null;
    }

    synchronized long routingBlockRemainingMs() {
        long blockedUntilMs = RealtimeRoutingPolicy.effectiveBlockedUntil(
                transportBlockedUntilMs, outcomeBlockedUntilMs);
        if (blockedUntilMs <= 0L) return 0L;
        return Math.max(0L, blockedUntilMs - System.currentTimeMillis());
    }

    synchronized long transportBlockRemainingMs() {
        if (transportBlockedUntilMs <= 0L) return 0L;
        return Math.max(0L, transportBlockedUntilMs - System.currentTimeMillis());
    }

    synchronized long outcomeBlockRemainingMs() {
        if (outcomeBlockedUntilMs <= 0L) return 0L;
        return Math.max(0L, outcomeBlockedUntilMs - System.currentTimeMillis());
    }

    synchronized int unstableReadyFailureStreak() {
        return Math.max(0, unstableReadyFailureStreak);
    }

    synchronized int routingOutcomePenalty() {
        decayRoutingPenaltiesLocked(System.currentTimeMillis());
        return RealtimeRoutingPolicy.effectiveOutcomePenalty(
                routingQualityPenalty, routingLatencyPenalty);
    }

    synchronized int routingQualityPenalty() {
        decayRoutingPenaltiesLocked(System.currentTimeMillis());
        return Math.max(0, routingQualityPenalty);
    }

    synchronized int routingLatencyPenalty() {
        decayRoutingPenaltiesLocked(System.currentTimeMillis());
        return Math.max(0, routingLatencyPenalty);
    }

    synchronized int goodRealtimeStreak() {
        return Math.max(0, goodRealtimeStreak);
    }

    synchronized long realtimeRouteLatencyEstimateMs() {
        return realtimeRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES
                ? realtimeRouteLatencyEstimateMs : -1L;
    }

    synchronized long fileRouteLatencyEstimateMs() {
        return fileRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES
                ? fileRouteLatencyEstimateMs : -1L;
    }

    synchronized long realtimeRouteLatencyJitterMs() {
        return realtimeRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES
                ? realtimeRouteLatencyJitterMs : -1L;
    }

    synchronized long fileRouteLatencyJitterMs() {
        return fileRouteLatencySamples >= RealtimeRoutingPolicy.MIN_ROUTE_LATENCY_SAMPLES
                ? fileRouteLatencyJitterMs : -1L;
    }

    synchronized long performanceProbeRemainingMs() {
        return RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                realtimeRouteLatencyEstimateMs, realtimeRouteLatencyJitterMs,
                realtimeRouteLatencySamples, realtimeRouteLatencySampleAtMs,
                fileRouteLatencyEstimateMs, fileRouteLatencyJitterMs,
                fileRouteLatencySamples, fileRouteLatencySampleAtMs,
                System.currentTimeMillis());
    }

    synchronized String lastRouteDecisionLabel() {
        return lastRouteDecisionLabel;
    }

    synchronized void noteRealtimeTranscriptOutcome(boolean acceptedUseful, boolean fileRecovery,
                                                     long latencyMs) {
        if (closed || !wanted) return;
        long now = System.currentTimeMillis();
        decayRoutingPenaltiesLocked(now);
        if (RealtimeRoutingPolicy.isRealtimePerformanceSampleEligible(
                acceptedUseful, fileRecovery, latencyMs)) {
            long previousEstimateMs = realtimeRouteLatencyEstimateMs;
            int previousSamples = realtimeRouteLatencySamples;
            long previousSampleAtMs = realtimeRouteLatencySampleAtMs;
            boolean previousEvidenceFresh = previousSampleAtMs > 0L
                    && now >= previousSampleAtMs
                    && now - previousSampleAtMs <= RealtimeRoutingPolicy.ROUTE_LATENCY_SAMPLE_MAX_AGE_MS;
            int nextDirection = RealtimeRoutingPolicy.routeLatencyOutlierDirection(
                    previousEstimateMs, previousSamples, latencyMs);
            int nextOutlierStreak = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                    realtimeRouteLatencyOutlierDirection, realtimeRouteLatencyOutlierStreak,
                    nextDirection, previousEvidenceFresh);
            realtimeRouteLatencyJitterMs = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                    realtimeRouteLatencyJitterMs, previousEstimateMs, previousSamples, latencyMs,
                    nextDirection, nextOutlierStreak);
            realtimeRouteLatencyEstimateMs = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                    previousEstimateMs, previousSamples, latencyMs,
                    nextDirection, nextOutlierStreak);
            realtimeRouteLatencySamples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(
                    previousSamples, latencyMs);
            realtimeRouteLatencySampleAtMs = now;
            realtimeRouteLatencyOutlierDirection = nextDirection;
            realtimeRouteLatencyOutlierStreak = nextOutlierStreak;
        }

        routingQualityPenalty = RealtimeRoutingPolicy.nextOutcomePenalty(
                routingQualityPenalty, acceptedUseful, fileRecovery);
        if (!fileRecovery) {
            routingLatencyPenalty = RealtimeRoutingPolicy.nextLatencyPenalty(
                    routingLatencyPenalty, latencyMs);
        }
        goodRealtimeStreak = RealtimeRoutingPolicy.nextGoodRealtimeStreak(
                goodRealtimeStreak, acceptedUseful, fileRecovery, latencyMs);
        lastRoutingSignalAtMs = now;

        if (RealtimeRoutingPolicy.shouldResetAfterGoodRealtime(goodRealtimeStreak)) {
            routingQualityPenalty = 0;
            routingLatencyPenalty = 0;
            goodRealtimeStreak = 0;
            outcomeBlockedUntilMs = 0L;
            return;
        }

        int effectivePenalty = RealtimeRoutingPolicy.effectiveOutcomePenalty(
                routingQualityPenalty, routingLatencyPenalty);
        boolean slowRealtimeLatency = !fileRecovery
                && RealtimeRoutingPolicy.isSlowRealtimeLatency(latencyMs);
        if (acceptedUseful && !fileRecovery && !slowRealtimeLatency) {
            if (effectivePenalty <= 0 || outcomeBlockedUntilMs <= now) {
                outcomeBlockedUntilMs = 0L;
            }
            return;
        }

        long blockMs = RealtimeRoutingPolicy.blockMsForOutcomePenalty(effectivePenalty);
        if (blockMs <= 0L) return;
        outcomeBlockedUntilMs = Math.max(outcomeBlockedUntilMs, now + blockMs);
    }

    synchronized void notePrimaryFileTurnOutcome(boolean usable, boolean performanceEligible,
                                                 long drainLatencyMs) {
        if (closed || !wanted) return;
        goodRealtimeStreak = 0;
        long now = System.currentTimeMillis();
        if (performanceEligible && drainLatencyMs >= 0L) {
            long previousEstimateMs = fileRouteLatencyEstimateMs;
            int previousSamples = fileRouteLatencySamples;
            long previousSampleAtMs = fileRouteLatencySampleAtMs;
            boolean previousEvidenceFresh = previousSampleAtMs > 0L
                    && now >= previousSampleAtMs
                    && now - previousSampleAtMs <= RealtimeRoutingPolicy.ROUTE_LATENCY_SAMPLE_MAX_AGE_MS;
            int nextDirection = RealtimeRoutingPolicy.routeLatencyOutlierDirection(
                    previousEstimateMs, previousSamples, drainLatencyMs);
            int nextOutlierStreak = RealtimeRoutingPolicy.nextRouteLatencyOutlierStreak(
                    fileRouteLatencyOutlierDirection, fileRouteLatencyOutlierStreak,
                    nextDirection, previousEvidenceFresh);
            fileRouteLatencyJitterMs = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                    fileRouteLatencyJitterMs, previousEstimateMs, previousSamples, drainLatencyMs,
                    nextDirection, nextOutlierStreak);
            fileRouteLatencyEstimateMs = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                    previousEstimateMs, previousSamples, drainLatencyMs,
                    nextDirection, nextOutlierStreak);
            fileRouteLatencySamples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(
                    previousSamples, drainLatencyMs);
            fileRouteLatencySampleAtMs = now;
            fileRouteLatencyOutlierDirection = nextDirection;
            fileRouteLatencyOutlierStreak = nextOutlierStreak;
        } else if (!performanceEligible) {
            // A degraded/cooldown file lane cannot keep winning on stale speed history,
            // even if its transcript coverage was still usable enough for context.
            fileRouteLatencyEstimateMs = -1L;
            fileRouteLatencyJitterMs = -1L;
            fileRouteLatencySamples = 0;
            fileRouteLatencySampleAtMs = 0L;
            fileRouteLatencyOutlierDirection = 0;
            fileRouteLatencyOutlierStreak = 0;
        }
        if (usable) return;

        decayRoutingPenaltiesLocked(now);
        routingQualityPenalty = RealtimeRoutingPolicy.penaltyAfterBadFile(
                routingQualityPenalty);
        routingLatencyPenalty = RealtimeRoutingPolicy.penaltyAfterBadFile(
                routingLatencyPenalty);
        goodRealtimeStreak = 0;
        lastRoutingSignalAtMs = now;
        outcomeBlockedUntilMs = RealtimeRoutingPolicy.shortenOutcomeBlockAfterBadFile(
                now, outcomeBlockedUntilMs);
    }

    private void decayRoutingPenaltiesLocked(long now) {
        if (lastRoutingSignalAtMs <= 0L || now <= lastRoutingSignalAtMs) return;
        long idleMs = now - lastRoutingSignalAtMs;
        if (idleMs < RealtimeRoutingPolicy.PENALTY_DECAY_STEP_MS) return;

        // A long idle gap breaks the meaning of "consecutive" healthy turns.
        goodRealtimeStreak = 0;
        routingQualityPenalty = RealtimeRoutingPolicy.decayedPenalty(
                routingQualityPenalty, idleMs, RealtimeRoutingPolicy.MAX_OUTCOME_PENALTY);
        routingLatencyPenalty = RealtimeRoutingPolicy.decayedPenalty(
                routingLatencyPenalty, idleMs, RealtimeRoutingPolicy.MAX_LATENCY_PENALTY);
        long steps = idleMs / RealtimeRoutingPolicy.PENALTY_DECAY_STEP_MS;
        lastRoutingSignalAtMs += steps * RealtimeRoutingPolicy.PENALTY_DECAY_STEP_MS;
        if (routingQualityPenalty <= 0 && routingLatencyPenalty <= 0
                && outcomeBlockedUntilMs <= now) {
            outcomeBlockedUntilMs = 0L;
        }
    }

    synchronized void shutdown() {
        if (closed) return;
        closed = true;
        stop();
        scheduler.shutdownNow();
        fallbackExecutor.shutdownNow();
        contextWindow.clear();
        try { httpClient.dispatcher().executorService().shutdown(); } catch (Throwable ignored) {}
        try { httpClient.connectionPool().evictAll(); } catch (Throwable ignored) {}
    }

    private void connectLocked() {
        if (!wanted || closed || socket != null) return;
        String key = SecretStore.loadApiKey(context);
        if (key.isEmpty()) {
            emitState("no_key");
            return;
        }

        emitState("connecting");
        Request request = new Request.Builder()
                .url(WS_URL)
                .header("Authorization", "Bearer " + key)
                .build();
        socket = httpClient.newWebSocket(request, new SocketListener());
    }

    private synchronized void handleOpen(WebSocket ws) {
        if (closed || !wanted || ws != socket) {
            try { ws.close(1000, "unused"); } catch (Throwable ignored) {}
            return;
        }

        JSONObject update = sessionUpdate();
        boolean sent;
        try { sent = ws.send(update.toString()); }
        catch (Throwable t) { sent = false; }
        if (!sent) {
            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
            emitState("fallback");
            try { ws.close(1011, "session_update_failed"); } catch (Throwable ignored) {}
            scheduleReconnect();
            return;
        }

        reconnectScheduled = false;
        ready = true;
        readyAtMs = System.currentTimeMillis();
        long stableGeneration = generation;
        scheduler.schedule(() -> markConnectionStable(ws, stableGeneration),
                RealtimeReconnectPolicy.STABLE_RESET_MS, TimeUnit.MILLISECONDS);
        emitState("ready");
    }

    private void handleMessage(WebSocket ws, String text) {
        JSONObject event;
        try { event = new JSONObject(text); }
        catch (Throwable ignored) { return; }

        String type = event.optString("type", "");
        if ("conversation.item.input_audio_transcription.delta".equals(type)) {
            String delta = event.optString("delta", "");
            String snapshot;
            long eventSpeechEpoch;
            synchronized (this) {
                if (ws != socket || (!turnActive && !awaitingCompletion) || delta.isEmpty()) return;
                eventSpeechEpoch = turnActive ? speechEpoch : committedSpeechEpoch;
                partial.append(delta);
                snapshot = partial.toString();
            }
            listener.onPartial(eventSpeechEpoch, snapshot);
            return;
        }

        if ("conversation.item.input_audio_transcription.completed".equals(type)) {
            String transcript = clean(event.optString("transcript", ""));
            long turn;
            long completedSpeechEpoch;
            long fallbackGeneration = -1L;
            synchronized (this) {
                if (ws != socket || !awaitingCompletion) return;
                turn = committedTurnSerial;
                completedSpeechEpoch = committedSpeechEpoch;
                awaitingCompletion = false;
                partial.setLength(0);
                reconnectAttempt = 0;
                if (!transcript.isEmpty()) {
                    clearPendingBackupLocked(turn);
                } else {
                    fallbackGeneration = generation;
                }
            }
            if (!transcript.isEmpty()) {
                listener.onFinal(turn, completedSpeechEpoch, TranscriptSource.REALTIME, transcript);
            } else {
                recoverCommittedTurn(turn, fallbackGeneration, "empty");
            }
            return;
        }

        if ("error".equals(type)) {
            long recoveryTurn = -1L;
            long recoveryGeneration = -1L;
            WebSocket failedSocket;
            synchronized (this) {
                if (ws != socket) return;
                if (awaitingCompletion) {
                    recoveryTurn = committedTurnSerial;
                    recoveryGeneration = generation;
                }
                failedSocket = socket;
                noteReadyFailureLocked();
                socket = null;
                ready = false;
                turnActive = false;
                awaitingCompletion = false;
            }
            emitState("fallback");
            if (failedSocket != null) {
                try { failedSocket.close(1011, "server_error"); } catch (Throwable ignored) {}
            }
            if (recoveryTurn > 0L) recoverCommittedTurn(recoveryTurn, recoveryGeneration, "server_error");
            scheduleReconnect();
        }
    }

    private void handleClosed(WebSocket ws) {
        long recoveryTurn = -1L;
        long recoveryGeneration = -1L;
        synchronized (this) {
            if (ws != socket) return;
            if (awaitingCompletion) {
                recoveryTurn = committedTurnSerial;
                recoveryGeneration = generation;
            }
            noteReadyFailureLocked();
            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
        }
        emitState("fallback");
        if (recoveryTurn > 0L) recoverCommittedTurn(recoveryTurn, recoveryGeneration, "closed");
        scheduleReconnect();
    }

    private void handleFailure(WebSocket ws) {
        long recoveryTurn = -1L;
        long recoveryGeneration = -1L;
        synchronized (this) {
            if (ws != socket) return;
            if (awaitingCompletion) {
                recoveryTurn = committedTurnSerial;
                recoveryGeneration = generation;
            }
            noteReadyFailureLocked();
            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
        }
        emitState("fallback");
        if (recoveryTurn > 0L) recoverCommittedTurn(recoveryTurn, recoveryGeneration, "failure");
        scheduleReconnect();
    }

    private void handleCommitTimeout(long turn, long timeoutGeneration, boolean finalDeadline) {
        WebSocket old;
        long timeoutSpeechEpoch;
        synchronized (this) {
            if (closed || !wanted || generation != timeoutGeneration) return;
            if (!awaitingCompletion || committedTurnSerial != turn) return;
            if (RealtimeCommitPolicy.grantPartialGrace(finalDeadline, partial.length() > 0)) {
                scheduler.schedule(() -> handleCommitTimeout(turn, timeoutGeneration, true),
                        RealtimeCommitPolicy.PARTIAL_GRACE_MS, TimeUnit.MILLISECONDS);
                return;
            }
            awaitingCompletion = false;
            noteReadyFailureLocked();
            ready = false;
            turnActive = false;
            partial.setLength(0);
            timeoutSpeechEpoch = committedSpeechEpoch;
            old = socket;
            socket = null;
        }

        listener.onPartial(timeoutSpeechEpoch, "");
        emitState("fallback");
        if (old != null) {
            try { old.close(1011, "transcription_timeout"); } catch (Throwable ignored) {}
        }
        recoverCommittedTurn(turn, timeoutGeneration, "timeout");
        scheduleReconnect();
    }

    private void recoverCommittedTurn(long turn, long recoveryGeneration, String reason) {
        final short[] audio;
        final int sampleRate;
        final long recoveryId;
        final long recoverySpeechEpoch;
        final long queuedAtMs = System.currentTimeMillis();
        synchronized (this) {
            if (turn <= 0L || pendingBackupTurnSerial != turn || pendingBackup.size() == 0) return;
            if (RealtimeRecoveryPolicy.isSuperseded(committedSpeechEpoch, speechEpoch)) {
                clearPendingBackupLocked(turn);
                return;
            }
            audio = pendingBackup.copy();
            sampleRate = pendingBackupSampleRate;
            recoverySpeechEpoch = committedSpeechEpoch;
            recoveryId = ++latestRecoveryId;
            clearPendingBackupLocked(turn);
        }

        emitState("file_fallback");
        fallbackExecutor.execute(() -> {
            if (!shouldDeliverRecovery(recoveryId, recoveryGeneration, queuedAtMs)) return;

            String transcript = "";
            try {
                short[] prepared = AudioPreprocessor.prepare(audio, sampleRate);
                if (prepared.length > 0) transcript = fallbackTranscribe(prepared, sampleRate);
            } catch (Throwable ignored) {}

            if (!shouldDeliverRecovery(recoveryId, recoveryGeneration, queuedAtMs)) return;

            if (!transcript.isEmpty()) {
                listener.onFinal(turn, recoverySpeechEpoch, TranscriptSource.FILE_RECOVERY, transcript);
            } else {
                emitStateUnlessReady("fallback_failed");
            }
        });
    }

    private synchronized boolean shouldDeliverRecovery(long recoveryId, long recoveryGeneration, long queuedAtMs) {
        long ageMs = Math.max(0L, System.currentTimeMillis() - queuedAtMs);
        boolean sessionMatches = !closed && wanted && generation == recoveryGeneration;
        return RealtimeRecoveryPolicy.shouldDeliver(
                recoveryId, latestRecoveryId, ageMs, sessionMatches);
    }

    private String fallbackTranscribe(short[] samples, int sampleRate) throws Exception {
        String key = SecretStore.loadApiKey(context);
        if (key.isEmpty()) return "";

        byte[] wav = wav(samples, sampleRate);
        MediaType wavType = MediaType.get("audio/wav");
        RequestBody fileBody = RequestBody.create(wav, wavType);
        MultipartBody.Builder multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "gpt-transcribe")
                .addFormDataPart("language", "bg")
                .addFormDataPart("prompt", fallbackPrompt())
                .addFormDataPart("response_format", "json")
                .addFormDataPart("file", "live.wav", fileBody);

        Request request = new Request.Builder()
                .url(FILE_STT_URL)
                .header("Authorization", "Bearer " + key)
                .post(multipart.build())
                .build();

        try (Response response = fallbackHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return clean(new JSONObject(response.body().string()).optString("text", ""));
        }
    }

    private String fallbackPrompt() {
        return buildTranscriptionPrompt(true);
    }

    private String realtimePrompt() {
        return buildTranscriptionPrompt(false);
    }

    private String buildTranscriptionPrompt(boolean fileFallback) {
        StringBuilder out = new StringBuilder(fileFallback
                ? "Български TikTok Live разговор. Транскрибирай точно бърза разговорна реч, имена, числа и кратки въпроси. Не измисляй несигурни думи."
                : "Български TikTok Live разговор. Разпознавай точно бърза разговорна реч, имена, числа и кратки въпроси. Не измисляй несигурни думи.");

        List<String> keywords = keywordHints();
        if (!keywords.isEmpty()) {
            out.append(" Възможни имена и термини: ");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) out.append(", ");
                out.append(keywords.get(i));
                if (out.length() >= 430) break;
            }
            out.append('.');
        }

        List<String> recent = contextWindow.snapshot(System.currentTimeMillis());
        if (!recent.isEmpty()) {
            String prefix = " Предишен контекст: ";
            int remaining = Math.max(0, 640 - out.length() - prefix.length() - 1);
            String recentText = SttPromptContextPolicy.buildRecent(recent, remaining);
            if (!recentText.isEmpty()) {
                out.append(prefix).append(recentText).append('.');
            }
        }

        if (out.length() > 650) return out.substring(0, 650);
        return out.toString();
    }

    void rememberAcceptedTranscript(String transcript) {
        contextWindow.add(transcript, System.currentTimeMillis());

        WebSocket ws;
        JSONObject update;
        synchronized (this) {
            if (!ready || socket == null || turnActive || awaitingCompletion || closed || !wanted) return;
            ws = socket;
            update = sessionUpdate();
        }
        boolean sent;
        try { sent = ws.send(update.toString()); }
        catch (Throwable ignored) { sent = false; }
        if (sent) return;

        synchronized (this) {
            if (ws != socket || closed || !wanted) return;
            noteReadyFailureLocked();
            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
        }
        emitState("fallback");
        try { ws.close(1011, "context_update_failed"); } catch (Throwable ignored) {}
        scheduleReconnect();
    }

    private void emitState(String state) {
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

    private void noteReadyFailureLocked() {
        long now = System.currentTimeMillis();
        goodRealtimeStreak = 0;
        if (RealtimeRoutingPolicy.isQuickFailure(
                readyAtMs, now, RealtimeReconnectPolicy.STABLE_RESET_MS)) {
            unstableReadyFailureStreak++;
            long blockMs = RealtimeRoutingPolicy.blockMsForUnstableStreak(
                    unstableReadyFailureStreak);
            if (blockMs > 0L) {
                transportBlockedUntilMs = Math.max(transportBlockedUntilMs, now + blockMs);
            }
        } else if (readyAtMs > 0L) {
            unstableReadyFailureStreak = 0;
            transportBlockedUntilMs = 0L;
        }
        readyAtMs = 0L;
    }

    private synchronized void markConnectionStable(WebSocket ws, long stableGeneration) {
        if (closed || !wanted || generation != stableGeneration || socket != ws || !ready) return;
        reconnectAttempt = 0;
        unstableReadyFailureStreak = 0;
        transportBlockedUntilMs = 0L;
    }

    private synchronized void scheduleReconnect() {
        if (!wanted || closed || reconnectScheduled || socket != null) return;
        reconnectScheduled = true;
        long delay = RealtimeReconnectPolicy.delayMs(reconnectAttempt++);
        scheduler.schedule(() -> {
            synchronized (RealtimeTranscriptionClient.this) {
                reconnectScheduled = false;
                if (wanted && !closed && socket == null) connectLocked();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private JSONObject sessionUpdate() {
        JSONObject root = new JSONObject();
        JSONObject session = new JSONObject();
        JSONObject audio = new JSONObject();
        JSONObject input = new JSONObject();
        JSONObject format = new JSONObject();
        JSONObject transcription = new JSONObject();

        try {
            root.put("type", "session.update");
            session.put("type", "transcription");
            format.put("type", "audio/pcm");
            format.put("rate", REALTIME_SAMPLE_RATE);
            transcription.put("model", "gpt-live-transcribe");
            transcription.put("prompt", realtimePrompt());
            transcription.put("languages", new JSONArray().put("bg"));
            transcription.put("delay", "low");

            JSONArray keywords = new JSONArray();
            for (String keyword : keywordHints()) keywords.put(keyword);
            if (keywords.length() > 0) transcription.put("keywords", keywords);

            input.put("format", format);
            input.put("transcription", transcription);
            input.put("turn_detection", JSONObject.NULL);
            audio.put("input", input);
            session.put("audio", audio);
            root.put("session", session);
        } catch (Throwable ignored) {}
        return root;
    }

    private List<String> keywordHints() {
        String raw = context.getSharedPreferences("live_copilot_ai", Context.MODE_PRIVATE)
                .getString("stt_keywords", "");
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String part : raw.split("[,;\\n]+")) {
            String clean = part.replace('<', ' ').replace('>', ' ').replace('\r', ' ').replace('\n', ' ').trim();
            if (clean.isEmpty()) continue;
            if (clean.length() > 80) clean = clean.substring(0, 80).trim();
            out.add(clean);
            if (out.size() >= 24) break;
        }
        return out;
    }

    private boolean sendClearLocked() {
        if (socket == null) return false;
        try {
            JSONObject event = new JSONObject();
            event.put("type", "input_audio_buffer.clear");
            return socket.send(event.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void clearPendingBackupLocked(long turn) {
        if (pendingBackupTurnSerial != turn) return;
        pendingBackup.clear();
        pendingBackupSampleRate = 16_000;
        pendingBackupTurnSerial = 0L;
    }

    private void clearBackupsLocked() {
        activeBackup.clear();
        pendingBackup.clear();
        activeBackupSampleRate = 16_000;
        pendingBackupSampleRate = 16_000;
        pendingBackupTurnSerial = 0L;
    }

    private static byte[] wav(short[] samples, int sampleRate) {
        int dataBytes = samples.length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R','I','F','F'});
        b.putInt(36 + dataBytes);
        b.put(new byte[]{'W','A','V','E'});
        b.put(new byte[]{'f','m','t',' '});
        b.putInt(16);
        b.putShort((short) 1);
        b.putShort((short) 1);
        b.putInt(sampleRate);
        b.putInt(sampleRate * 2);
        b.putShort((short) 2);
        b.putShort((short) 16);
        b.put(new byte[]{'d','a','t','a'});
        b.putInt(dataBytes);
        for (short sample : samples) b.putShort(sample);
        return b.array();
    }

    private static byte[] toLittleEndian(short[] samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) buffer.putShort(sample);
        return buffer.array();
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private final class SocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            handleOpen(webSocket);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            handleMessage(webSocket, text);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            handleClosed(webSocket);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            handleFailure(webSocket);
        }
    }
}
