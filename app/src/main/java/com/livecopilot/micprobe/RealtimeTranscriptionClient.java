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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class RealtimeTranscriptionClient {
    interface Listener {
        void onState(String state);
        void onPartial(String partial);
        void onFinal(long turnSerial, String transcript);
    }

    private static final String WS_URL = "wss://api.openai.com/v1/realtime?model=gpt-live-transcribe";
    private static final int REALTIME_SAMPLE_RATE = 24_000;
    private static final int MIN_COMMIT_SAMPLES = REALTIME_SAMPLE_RATE / 10; // 100 ms

    private final Context context;
    private final Listener listener;
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final StreamingAudioPreprocessor streamingPreprocessor = new StreamingAudioPreprocessor();

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
    private int sentSamples;
    private StringBuilder partial = new StringBuilder();

    RealtimeTranscriptionClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .connectTimeout(12, TimeUnit.SECONDS)
                .build();
    }

    synchronized void start() {
        if (closed) return;
        wanted = true;
        if (socket == null) connectLocked();
    }

    synchronized void stop() {
        wanted = false;
        ready = false;
        turnActive = false;
        awaitingCompletion = false;
        sentSamples = 0;
        partial.setLength(0);
        WebSocket old = socket;
        socket = null;
        if (old != null) {
            try { old.close(1000, "pause"); } catch (Throwable ignored) {}
        }
        listener.onState("off");
    }

    synchronized boolean beginTurn(int sourceSampleRate) {
        if (!ready || socket == null || turnActive || awaitingCompletion || sourceSampleRate <= 0) return false;
        turnActive = true;
        activeTurnSerial = ++serial;
        sentSamples = 0;
        partial.setLength(0);
        streamingPreprocessor.reset(sourceSampleRate);
        return true;
    }

    synchronized boolean append(short[] pcm16, int sourceSampleRate) {
        if (!turnActive || !ready || socket == null || pcm16 == null || pcm16.length == 0) return false;
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

        boolean sent;
        try { sent = socket.send(event.toString()); }
        catch (Throwable t) { sent = false; }
        if (!sent) {
            turnActive = false;
            ready = false;
            listener.onState("fallback");
            return false;
        }
        sentSamples += realtime.length;
        return true;
    }

    synchronized boolean commitTurn() {
        if (!turnActive) return false;
        turnActive = false;
        if (!ready || socket == null || sentSamples < MIN_COMMIT_SAMPLES) {
            sentSamples = 0;
            sendClearLocked();
            return false;
        }

        JSONObject event = new JSONObject();
        try { event.put("type", "input_audio_buffer.commit"); }
        catch (Throwable ignored) { return false; }

        boolean sent;
        try { sent = socket.send(event.toString()); }
        catch (Throwable t) { sent = false; }
        if (!sent) {
            sentSamples = 0;
            ready = false;
            listener.onState("fallback");
            return false;
        }

        committedTurnSerial = activeTurnSerial;
        awaitingCompletion = true;
        sentSamples = 0;
        return true;
    }

    synchronized boolean isReady() {
        return ready && socket != null;
    }

    synchronized void shutdown() {
        if (closed) return;
        closed = true;
        stop();
        scheduler.shutdownNow();
        try { httpClient.dispatcher().executorService().shutdown(); } catch (Throwable ignored) {}
        try { httpClient.connectionPool().evictAll(); } catch (Throwable ignored) {}
    }

    private void connectLocked() {
        if (!wanted || closed || socket != null) return;
        String key = SecretStore.loadApiKey(context);
        if (key.isEmpty()) {
            listener.onState("no_key");
            return;
        }

        listener.onState("connecting");
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
            ready = false;
            listener.onState("fallback");
            try { ws.close(1011, "session_update_failed"); } catch (Throwable ignored) {}
            return;
        }

        reconnectAttempt = 0;
        reconnectScheduled = false;
        ready = true;
        listener.onState("ready");
    }

    private void handleMessage(WebSocket ws, String text) {
        JSONObject event;
        try { event = new JSONObject(text); }
        catch (Throwable ignored) { return; }

        String type = event.optString("type", "");
        if ("conversation.item.input_audio_transcription.delta".equals(type)) {
            String delta = event.optString("delta", "");
            String snapshot;
            synchronized (this) {
                if (ws != socket || !awaitingCompletion || delta.isEmpty()) return;
                partial.append(delta);
                snapshot = partial.toString();
            }
            listener.onPartial(snapshot);
            return;
        }

        if ("conversation.item.input_audio_transcription.completed".equals(type)) {
            String transcript = clean(event.optString("transcript", ""));
            long turn;
            synchronized (this) {
                if (ws != socket || !awaitingCompletion) return;
                turn = committedTurnSerial;
                awaitingCompletion = false;
                partial.setLength(0);
            }
            if (!transcript.isEmpty()) listener.onFinal(turn, transcript);
            return;
        }

        if ("error".equals(type)) {
            synchronized (this) {
                if (ws != socket) return;
                ready = false;
                turnActive = false;
                awaitingCompletion = false;
            }
            listener.onState("fallback");
        }
    }

    private void handleClosed(WebSocket ws) {
        synchronized (this) {
            if (ws != socket) return;
            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
        }
        listener.onState("fallback");
        scheduleReconnect();
    }

    private void handleFailure(WebSocket ws) {
        synchronized (this) {
            if (ws != socket) return;
            socket = null;
            ready = false;
            turnActive = false;
            awaitingCompletion = false;
        }
        listener.onState("fallback");
        scheduleReconnect();
    }

    private synchronized void scheduleReconnect() {
        if (!wanted || closed || reconnectScheduled || socket != null) return;
        reconnectScheduled = true;
        long delay = Math.min(10_000L, 1_000L << Math.min(3, reconnectAttempt++));
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
            transcription.put("prompt", "Български TikTok Live разговор. Разпознавай точно бърза разговорна реч, имена, числа и кратки въпроси.");
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

    private synchronized void sendClearLocked() {
        if (socket == null) return;
        try {
            JSONObject event = new JSONObject();
            event.put("type", "input_audio_buffer.clear");
            socket.send(event.toString());
        } catch (Throwable ignored) {}
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
