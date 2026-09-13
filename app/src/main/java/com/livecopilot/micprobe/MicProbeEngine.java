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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

final class MicProbeEngine {
    interface Listener {
        void onSnapshot(Snapshot snapshot);
        void onPcmChunk(short[] samples, int sampleRate);
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
    private static final int AI_CHUNK_SECONDS = 4;

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
            emit(new Snapshot(false, false, -90.0, 0,
                    "Няма RECORD_AUDIO. Върни се в приложението и разреши микрофона."));
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
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (minBuffer <= 0) {
            fail("AudioRecord.getMinBufferSize() върна " + minBuffer);
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
                fail("AudioRecord не се инициализира.");
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
                fail("AudioRecord не влезе в RECORDSTATE_RECORDING.");
                safeRelease();
                return;
            }

            startedAtMs = SystemClock.elapsedRealtime();
            running.set(true);
            finalStatus = "recording";
            emit(snapshot("Слушам. AI обработва на кратки аудио сегменти."));

            readThread = new Thread(() -> readLoop(bufferBytes), "MicProbeAudioReader");
            readThread.start();
        } catch (SecurityException se) {
            fail("SecurityException: " + safeMessage(se));
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
            try { t.join(500); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }

        long duration = startedAtMs == 0 ? 0 : Math.max(0, SystemClock.elapsedRealtime() - startedAtMs);
        ProbeResultStore.save(context, duration, silenceEvents, peakDb, audibleWindows, tiktokSeen, finalStatus);

        safeRelease();
        emit(new Snapshot(false, lastSilenced, peakDb, silenceEvents,
                "Спряно. Натисни START за нова AI сесия."));
    }

    private void readLoop(int bufferBytes) {
        short[] buffer = new short[Math.max(1024, bufferBytes / 2)];
        short[] aiChunk = new short[SAMPLE_RATE * AI_CHUNK_SECONDS];
        int aiPos = 0;
        int voicedReads = 0;
        long lastUiUpdate = 0;

        while (running.get()) {
            AudioRecord r = recorder;
            if (r == null) break;

            int read;
            try {
                read = r.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
            } catch (Throwable t) {
                if (running.get()) {
                    finalStatus = "read_error";
                    emit(new Snapshot(false, lastSilenced, peakDb, silenceEvents,
                            "Грешка при микрофона: " + safeMessage(t)));
                }
                break;
            }

            if (read <= 0) {
                if (read == AudioRecord.ERROR_DEAD_OBJECT) {
                    finalStatus = "dead_object";
                    break;
                }
                continue;
            }

            double sumSquares = 0.0;
            for (int i = 0; i < read; i++) {
                double normalized = buffer[i] / 32768.0;
                sumSquares += normalized * normalized;
            }
            double rms = Math.sqrt(sumSquares / read);
            double db = rms <= 0.000001 ? -90.0 : 20.0 * Math.log10(rms);
            db = Math.max(-90.0, Math.min(0.0, db));
            peakDb = Math.max(peakDb, db);

            boolean silenced = queryClientSilenced();
            updateSilenceState(silenced);
            if (!silenced && db > -55.0) audibleWindows++;
            if (!silenced && db > -48.0) voicedReads++;

            int offset = 0;
            while (offset < read) {
                int n = Math.min(read - offset, aiChunk.length - aiPos);
                System.arraycopy(buffer, offset, aiChunk, aiPos, n);
                aiPos += n;
                offset += n;
                if (aiPos == aiChunk.length) {
                    if (!silenced && voicedReads >= 2) {
                        listener.onPcmChunk(Arrays.copyOf(aiChunk, aiChunk.length), SAMPLE_RATE);
                    }
                    aiPos = 0;
                    voicedReads = 0;
                }
            }

            long now = SystemClock.elapsedRealtime();
            if (now - lastUiUpdate >= 200) {
                lastUiUpdate = now;
                String s = silenced
                        ? "ANDROID Е ЗАГЛУШИЛ НАШИЯ MIC CLIENT"
                        : String.format(Locale.US, "Mic %.1f dBFS • AI слуша", db);
                emit(new Snapshot(true, silenced, db, silenceEvents, s));
            }
        }
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
}
