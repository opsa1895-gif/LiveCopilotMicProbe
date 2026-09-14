from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:180]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


client = "app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java"
service = "app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java"
policy = "app/src/main/java/com/livecopilot/micprobe/RealtimeRecoveryPolicy.java"
test = "app/src/test/java/com/livecopilot/micprobe/RealtimeRecoveryPolicyTest.java"
gradle = "app/build.gradle.kts"

Path(policy).write_text('''package com.livecopilot.micprobe;\n\nfinal class RealtimeRecoveryPolicy {\n    static final long MAX_DELIVERY_AGE_MS = 8_000L;\n\n    private RealtimeRecoveryPolicy() {}\n\n    static boolean isSuperseded(long committedSpeechEpoch, long currentSpeechEpoch) {\n        return committedSpeechEpoch != currentSpeechEpoch;\n    }\n\n    static boolean shouldDeliver(long recoveryId, long latestRecoveryId, long ageMs, boolean sessionMatches) {\n        return sessionMatches\n                && recoveryId > 0L\n                && recoveryId == latestRecoveryId\n                && ageMs >= 0L\n                && ageMs <= MAX_DELIVERY_AGE_MS;\n    }\n}\n''', encoding="utf-8")

Path(test).write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n\npublic class RealtimeRecoveryPolicyTest {\n    @Test\n    public void latestFreshRecoveryCanDeliver() {\n        assertTrue(RealtimeRecoveryPolicy.shouldDeliver(3L, 3L, 2_000L, true));\n    }\n\n    @Test\n    public void olderRecoveryCannotOverwriteNewerSpeech() {\n        assertFalse(RealtimeRecoveryPolicy.shouldDeliver(2L, 3L, 1_000L, true));\n    }\n\n    @Test\n    public void staleRecoveryIsDropped() {\n        assertFalse(RealtimeRecoveryPolicy.shouldDeliver(\n                3L, 3L, RealtimeRecoveryPolicy.MAX_DELIVERY_AGE_MS + 1L, true));\n    }\n\n    @Test\n    public void oldSessionCannotDeliver() {\n        assertFalse(RealtimeRecoveryPolicy.shouldDeliver(3L, 3L, 1_000L, false));\n    }\n\n    @Test\n    public void recoveryFromOlderSpeechEpochIsSuperseded() {\n        assertTrue(RealtimeRecoveryPolicy.isSuperseded(4L, 5L));\n        assertFalse(RealtimeRecoveryPolicy.isSuperseded(5L, 5L));\n    }\n}\n''', encoding="utf-8")

replace_once(client,
    'import java.util.concurrent.ExecutorService;\n',
    '')

replace_once(client,
    '''    private final Listener listener;\n    private final OkHttpClient httpClient;\n    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();\n    private final ExecutorService fallbackExecutor = Executors.newSingleThreadExecutor();\n''',
    '''    private final Listener listener;\n    private final OkHttpClient httpClient;\n    private final OkHttpClient fallbackHttpClient;\n    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();\n    private final LatestWinsExecutor fallbackExecutor = new LatestWinsExecutor(2);\n''')

replace_once(client,
    '''    private long pendingBackupTurnSerial;\n    private long generation;\n    private int activeBackupSampleRate = 16_000;\n''',
    '''    private long pendingBackupTurnSerial;\n    private long generation;\n    private long speechEpoch;\n    private long committedSpeechEpoch;\n    private long latestRecoveryId;\n    private int activeBackupSampleRate = 16_000;\n''')

replace_once(client,
    '''        this.httpClient = new OkHttpClient.Builder()\n                .pingInterval(20, TimeUnit.SECONDS)\n                .connectTimeout(12, TimeUnit.SECONDS)\n                .readTimeout(40, TimeUnit.SECONDS)\n                .writeTimeout(40, TimeUnit.SECONDS)\n                .build();\n''',
    '''        this.httpClient = new OkHttpClient.Builder()\n                .pingInterval(20, TimeUnit.SECONDS)\n                .connectTimeout(12, TimeUnit.SECONDS)\n                .readTimeout(40, TimeUnit.SECONDS)\n                .writeTimeout(40, TimeUnit.SECONDS)\n                .build();\n        this.fallbackHttpClient = this.httpClient.newBuilder()\n                .callTimeout(12, TimeUnit.SECONDS)\n                .connectTimeout(5, TimeUnit.SECONDS)\n                .readTimeout(9, TimeUnit.SECONDS)\n                .writeTimeout(9, TimeUnit.SECONDS)\n                .build();\n''')

replace_once(client,
    '''    synchronized boolean beginTurn(int sourceSampleRate) {\n''',
    '''    synchronized void noteNewSpeech() {\n        speechEpoch++;\n        latestRecoveryId++;\n    }\n\n    synchronized boolean beginTurn(int sourceSampleRate) {\n''')

replace_once(client,
    '''        committedTurnSerial = activeTurnSerial;\n        awaitingCompletion = true;\n''',
    '''        committedTurnSerial = activeTurnSerial;\n        committedSpeechEpoch = speechEpoch;\n        awaitingCompletion = true;\n''')

replace_once(client,
    '''    private void recoverCommittedTurn(long turn, long recoveryGeneration, String reason) {\n        final short[] audio;\n        final int sampleRate;\n        synchronized (this) {\n            if (turn <= 0L || pendingBackupTurnSerial != turn || pendingBackup.size() == 0) return;\n            audio = pendingBackup.copy();\n            sampleRate = pendingBackupSampleRate;\n            clearPendingBackupLocked(turn);\n        }\n\n        listener.onState("file_fallback");\n        fallbackExecutor.execute(() -> {\n            String transcript = "";\n            try {\n                short[] prepared = AudioPreprocessor.prepare(audio, sampleRate);\n                if (prepared.length > 0) transcript = fallbackTranscribe(prepared, sampleRate);\n            } catch (Throwable ignored) {}\n\n            boolean deliver;\n            synchronized (RealtimeTranscriptionClient.this) {\n                deliver = !closed && wanted && generation == recoveryGeneration;\n            }\n            if (!deliver) return;\n\n            if (!transcript.isEmpty()) {\n                listener.onFinal(turn, transcript);\n            } else {\n                listener.onState("fallback_failed");\n            }\n        });\n    }\n''',
    '''    private void recoverCommittedTurn(long turn, long recoveryGeneration, String reason) {\n        final short[] audio;\n        final int sampleRate;\n        final long recoveryId;\n        final long queuedAtMs = System.currentTimeMillis();\n        synchronized (this) {\n            if (turn <= 0L || pendingBackupTurnSerial != turn || pendingBackup.size() == 0) return;\n            if (RealtimeRecoveryPolicy.isSuperseded(committedSpeechEpoch, speechEpoch)) {\n                clearPendingBackupLocked(turn);\n                return;\n            }\n            audio = pendingBackup.copy();\n            sampleRate = pendingBackupSampleRate;\n            recoveryId = ++latestRecoveryId;\n            clearPendingBackupLocked(turn);\n        }\n\n        listener.onState("file_fallback");\n        fallbackExecutor.execute(() -> {\n            if (!shouldDeliverRecovery(recoveryId, recoveryGeneration, queuedAtMs)) return;\n\n            String transcript = "";\n            try {\n                short[] prepared = AudioPreprocessor.prepare(audio, sampleRate);\n                if (prepared.length > 0) transcript = fallbackTranscribe(prepared, sampleRate);\n            } catch (Throwable ignored) {}\n\n            if (!shouldDeliverRecovery(recoveryId, recoveryGeneration, queuedAtMs)) return;\n\n            if (!transcript.isEmpty()) {\n                listener.onFinal(turn, transcript);\n            } else {\n                listener.onState("fallback_failed");\n            }\n        });\n    }\n\n    private synchronized boolean shouldDeliverRecovery(long recoveryId, long recoveryGeneration, long queuedAtMs) {\n        long ageMs = Math.max(0L, System.currentTimeMillis() - queuedAtMs);\n        boolean sessionMatches = !closed && wanted && generation == recoveryGeneration;\n        return RealtimeRecoveryPolicy.shouldDeliver(\n                recoveryId, latestRecoveryId, ageMs, sessionMatches);\n    }\n''')

replace_once(client,
    '''        try (Response response = httpClient.newCall(request).execute()) {\n''',
    '''        try (Response response = fallbackHttpClient.newCall(request).execute()) {\n''')

replace_once(service,
    '''    public void onStreamTurnStart(int sampleRate) {\n        clearFallbackTurnAudio();\n        realtimeTurnActive = realtimeTranscriber != null && realtimeTranscriber.beginTurn(sampleRate);\n    }\n''',
    '''    public void onStreamTurnStart(int sampleRate) {\n        clearFallbackTurnAudio();\n        if (realtimeTranscriber != null) realtimeTranscriber.noteNewSpeech();\n        realtimeTurnActive = realtimeTranscriber != null && realtimeTranscriber.beginTurn(sampleRate);\n    }\n''')

replace_once(gradle,
    '''        versionCode = 41\n        versionName = "0.40.0-reconnect-stability"\n''',
    '''        versionCode = 42\n        versionName = "0.41.0-latest-recovery"\n''')
