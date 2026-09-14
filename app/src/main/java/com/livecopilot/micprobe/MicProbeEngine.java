package com.livecopilot.micprobe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class MicProbeEngine {
    interface Listener {
        void onSnapshot(Snapshot snapshot);
        void onPcmChunk(short[] samples, int sampleRate);
        void onVoiceActivity(boolean speaking);
        void onStreamTurnStart(int sampleRate);
        void onPcmStream(short[] samples, int sampleRate);
        void onStreamTurnEnd();
    }

    static final class Snapshot {
        final boolean running;
        final boolean clientSilenced;
        final double dbfs;
        final int silenceEvents;
        final String status;

        Snapshot(boolean running, boolean clientSilenced, double dbfs, int silenceEvents, String status) {
            this.running = running;
            this.clientSilenced = clientSilenced;
            this.dbfs = dbfs;
            this.silenceEvents = silenceEvents;
            this.status = status;
        }
    }

    private static final int SAMPLE_RATE = 16_000;
    private static final int FRAME_SAMPLES = 320; // 20 ms
    private static final int PRE_ROLL_FRAMES = 20; // 400 ms
    private static final int START_VOICE_FRAMES = 2; // 40 ms
    private static final int MIN_SEGMENT_SAMPLES = SAMPLE_RATE * 3 / 4;
    private static final int MAX_SEGMENT_SAMPLES = SAMPLE_RATE * 6;
    private static final int OVERLAP_SAMPLES = (int) (SAMPLE_RATE * 0.8); // forced long-turn split only

    private final Context context;
    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private AudioRecord recorder;
    private Thread readThread;
    private AudioManager.AudioRecordingCallback recordingCallback;

    private long startedAtMs;
    private int silenceEvents;
    private boolean lastSilenced;
    private double peakDb = -90.0;
    private int audibleWindows;
    private volatile boolean tiktokSeen;
    private volatile String finalStatus = "idle";

    MicProbeEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    boolean isRunning() {
        return running.get();
    }

    void markTikTokSeen() {
        if (running.get()) tiktokSeen = true;
    }

    void start() {
        if (running.get()) return;

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            emit(new Snapshot(false, false, -90.0, 0, "Няма разрешение за микрофона."));
            return;
        }

        silenceEvents = 0;
        lastSilenced = false;
        peakDb = -90.0;
        audibleWindows = 0;
        tiktokSeen = false;
        finalStatus = "starting";

        final int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        if (minBuffer <= 0) {
            fail("AudioRecord buffer error: " + minBuffer);
            return;
        }

        final int bufferBytes = Math.max(minBuffer * 2, 8_192);

        try {
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();

            AudioRecord.Builder builder = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes);

            if (Build.VERSION.SDK_INT >= 30) builder.setPrivacySensitive(false);

            recorder = builder.build();
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                fail("Микрофонът не се инициализира.");
                safeRelease();
                return;
            }

            recordingCallback = new AudioManager.AudioRecordingCallback() {
                @Override
                public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                    AudioRecord r = recorder;
                    if (r == null || !running.get()) return;
                    AudioRecordingConfiguration cfg = r.getActiveRecordingConfiguration();
                    if (cfg != null) updateSilenceState(cfg.isClientSilenced());
                }
            };
            recorder.registerAudioRecordingCallback(context.getMainExecutor(), recordingCallback);

            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                fail("Микрофонът не стартира запис.");
                safeRelease();
                return;
            }

            startedAtMs = SystemClock.elapsedRealtime();
            running.set(true);
            finalStatus = "recording";
            emit(snapshot("Слушам"));

            readThread = new Thread(() -> readLoop(bufferBytes), "LiveCopilotAudioReader");
            readThread.start();
        } catch (SecurityException se) {
            fail("Няма достъп до микрофона.");
            safeRelease();
        } catch (Throwable t) {
            fail(t.getClass().getSimpleName() + ": " + safeMessage(t));
            safeRelease();
        }
    }

    void stop(String reason) {
        if (!running.getAndSet(false)) {
            safeRelease();
            return;
        }

        finalStatus = reason == null ? "stopped" : reason;
        AudioRecord r = recorder;
        if (r != null) {
            try {
                if (r.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) r.stop();
            } catch (Throwable ignored) {}
        }

        Thread t = readThread;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(700); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }

        long duration = startedAtMs == 0 ? 0 : Math.max(0, SystemClock.elapsedRealtime() - startedAtMs);
        ProbeResultStore.save(context, duration, silenceEvents, peakDb, audibleWindows, tiktokSeen, finalStatus);

        safeRelease();
        emitVoice(false);
        emit(new Snapshot(false, lastSilenced, peakDb, silenceEvents, "Спряно"));
    }

    private void readLoop(int bufferBytes) {
        short[] readBuffer = new short[Math.max(2048, bufferBytes / 2)];
        short[] frame = new short[FRAME_SAMPLES];
        int framePos = 0;

        ArrayDeque<short[]> preRoll = new ArrayDeque<>();
        ShortAccumulator segment = new ShortAccumulator(SAMPLE_RATE * 3);

        boolean speaking = false;
        int consecutiveVoiceFrames = 0;
        int consecutiveSilenceFrames = 0;
        int voicedFramesInSegment = 0;
        double noiseFloorDb = -62.0;
        double latestDb = -90.0;
        long lastUiUpdate = 0L;

        while (running.get()) {
            AudioRecord r = recorder;
            if (r == null) break;

            int read;
            try {
                read = r.read(readBuffer, 0, readBuffer.length, AudioRecord.READ_BLOCKING);
            } catch (Throwable t) {
                if (running.get()) {
                    finalStatus = "read_error";
                    emit(new Snapshot(false, lastSilenced, peakDb, silenceEvents,
                            "Грешка при микрофона: " + safeMessage(t)));
                }
                break;
            }

            if (read <= 0) {
                if (read == AudioRecord.ERROR_DEAD_OBJECT) finalStatus = "dead_object";
                continue;
            }

            boolean silenced = queryClientSilenced();
            updateSilenceState(silenced);

            for (int i = 0; i < read; i++) {
                frame[framePos++] = readBuffer[i];
                if (framePos < FRAME_SAMPLES) continue;

                latestDb = dbfs(frame, FRAME_SAMPLES);
                peakDb = Math.max(peakDb, latestDb);
                if (!silenced && latestDb > -55.0) audibleWindows++;

                if (!speaking && latestDb < -30.0) {
                    noiseFloorDb = noiseFloorDb * 0.97 + latestDb * 0.03;
                }
                boolean voiced = VoiceActivityPolicy.isVoiced(
                        latestDb, noiseFloorDb, speaking, silenced);

                short[] frameCopy = frame.clone();

                if (!speaking) {
                    preRoll.addLast(frameCopy);
                    while (preRoll.size() > PRE_ROLL_FRAMES) preRoll.removeFirst();

                    consecutiveVoiceFrames = voiced ? consecutiveVoiceFrames + 1 : 0;
                    if (consecutiveVoiceFrames >= START_VOICE_FRAMES) {
                        speaking = true;
                        consecutiveSilenceFrames = 0;
                        voicedFramesInSegment = consecutiveVoiceFrames;
                        segment.clear();
                        listener.onStreamTurnStart(SAMPLE_RATE);
                        for (short[] old : preRoll) {
                            segment.append(old, old.length);
                            listener.onPcmStream(old, SAMPLE_RATE);
                        }
                        preRoll.clear();
                        emitVoice(true);
                    }
                } else {
                    segment.append(frameCopy, frameCopy.length);
                    listener.onPcmStream(frameCopy, SAMPLE_RATE);
                    if (voiced) {
                        consecutiveSilenceFrames = 0;
                        voicedFramesInSegment++;
                    } else {
                        consecutiveSilenceFrames++;
                    }

                    if (segment.size() >= MAX_SEGMENT_SAMPLES) {
                        emitSegment(segment, voicedFramesInSegment);
                        short[] overlap = segment.tail(OVERLAP_SAMPLES);
                        segment.clear();
                        segment.append(overlap, overlap.length);
                        voicedFramesInSegment = voiced ? 1 : 0;
                        consecutiveSilenceFrames = voiced ? 0 : consecutiveSilenceFrames;
                    } else if (SpeechTurnPolicy.shouldEndTurn(
                            consecutiveSilenceFrames,
                            segment.size(),
                            voicedFramesInSegment,
                            SAMPLE_RATE,
                            FRAME_SAMPLES,
                            MIN_SEGMENT_SAMPLES)) {
                        emitSegment(segment, voicedFramesInSegment);
                        listener.onStreamTurnEnd();
                        segment.clear();
                        speaking = false;
                        consecutiveVoiceFrames = 0;
                        consecutiveSilenceFrames = 0;
                        voicedFramesInSegment = 0;
                        emitVoice(false);
                    }
                }

                framePos = 0;
            }

            long now = SystemClock.elapsedRealtime();
            if (now - lastUiUpdate >= 300L) {
                lastUiUpdate = now;
                String s = silenced
                        ? "Mic блокиран от Android"
                        : (speaking ? "Чувам реч" : "Слушам");
                emit(new Snapshot(true, silenced, latestDb, silenceEvents, s));
            }
        }

        if (segment.size() >= MIN_SEGMENT_SAMPLES && voicedFramesInSegment >= 4) {
            emitSegment(segment, voicedFramesInSegment);
        }
        if (speaking) {
            listener.onStreamTurnEnd();
            emitVoice(false);
        }
    }

    private void emitSegment(ShortAccumulator segment, int voicedFrames) {
        if (voicedFrames < 4 || segment.size() < MIN_SEGMENT_SAMPLES) return;
        listener.onPcmChunk(segment.toArray(), SAMPLE_RATE);
    }

    private static double dbfs(short[] samples, int length) {
        double sumSquares = 0.0;
        for (int i = 0; i < length; i++) {
            double normalized = samples[i] / 32768.0;
            sumSquares += normalized * normalized;
        }
        double rms = Math.sqrt(sumSquares / Math.max(1, length));
        if (rms <= 0.000001) return -90.0;
        return clamp(20.0 * Math.log10(rms), -90.0, 0.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean queryClientSilenced() {
        AudioRecord r = recorder;
        if (r == null) return false;
        try {
            AudioRecordingConfiguration cfg = r.getActiveRecordingConfiguration();
            return cfg != null && cfg.isClientSilenced();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private synchronized void updateSilenceState(boolean silenced) {
        if (silenced && !lastSilenced) silenceEvents++;
        lastSilenced = silenced;
    }

    private Snapshot snapshot(String status) {
        return new Snapshot(running.get(), queryClientSilenced(), peakDb, silenceEvents, status);
    }

    private void fail(String message) {
        finalStatus = "error";
        running.set(false);
        emit(new Snapshot(false, false, -90.0, silenceEvents, message));
    }

    private void emit(Snapshot snapshot) {
        context.getMainExecutor().execute(() -> listener.onSnapshot(snapshot));
    }

    private void emitVoice(boolean speaking) {
        context.getMainExecutor().execute(() -> listener.onVoiceActivity(speaking));
    }

    private void safeRelease() {
        AudioRecord r = recorder;
        recorder = null;
        readThread = null;
        if (r != null) {
            try {
                if (recordingCallback != null) r.unregisterAudioRecordingCallback(recordingCallback);
            } catch (Throwable ignored) {}
            recordingCallback = null;
            try { r.release(); } catch (Throwable ignored) {}
        }
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null ? "без допълнително съобщение" : m;
    }

    private static final class ShortAccumulator {
        private short[] data;
        private int size;

        ShortAccumulator(int initialCapacity) {
            data = new short[Math.max(1024, initialCapacity)];
        }

        int size() {
            return size;
        }

        void clear() {
            size = 0;
        }

        void append(short[] src, int length) {
            if (src == null || length <= 0) return;
            int n = Math.min(length, src.length);
            ensure(size + n);
            System.arraycopy(src, 0, data, size, n);
            size += n;
        }

        short[] tail(int count) {
            int n = Math.min(Math.max(0, count), size);
            short[] out = new short[n];
            System.arraycopy(data, size - n, out, 0, n);
            return out;
        }

        short[] toArray() {
            short[] out = new short[size];
            System.arraycopy(data, 0, out, 0, size);
            return out;
        }

        private void ensure(int needed) {
            if (needed <= data.length) return;
            int newSize = Math.max(needed, data.length * 2);
            short[] next = new short[newSize];
            System.arraycopy(data, 0, next, 0, size);
            data = next;
        }
    }
}
