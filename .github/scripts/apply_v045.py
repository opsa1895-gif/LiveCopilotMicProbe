from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:180]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


client = "app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java"
gradle = "app/build.gradle.kts"

replace_once(
    client,
    '''        final long createdAtMs;\n        final long sessionSerial;\n\n        AudioItem(short[] samples, int sampleRate, long sessionSerial) {\n            this.samples = samples;\n            this.sampleRate = sampleRate;\n            this.createdAtMs = System.currentTimeMillis();\n            this.sessionSerial = sessionSerial;\n        }\n''',
    '''        final long createdAtMs;\n        final long sessionSerial;\n        final long inputSerial;\n\n        AudioItem(short[] samples, int sampleRate, long sessionSerial, long inputSerial) {\n            this.samples = samples;\n            this.sampleRate = sampleRate;\n            this.createdAtMs = System.currentTimeMillis();\n            this.sessionSerial = sessionSerial;\n            this.inputSerial = inputSerial;\n        }\n''',
)

replace_once(
    client,
    '''    private static final long MAX_AUDIO_AGE_MS = 10_000L;\n''',
    '''    private static final long MAX_AUDIO_AGE_MS = FileSttFreshnessPolicy.MAX_SURFACE_AGE_MS;\n''',
)

replace_once(
    client,
    '''    private long sessionSerial = 1L;\n    private long latestReplySerial;\n''',
    '''    private long sessionSerial = 1L;\n    private long latestInputSerial;\n    private long latestReplySerial;\n''',
)

replace_once(
    client,
    '''        sessionSerial++;\n        latestReplySerial++;\n        audioQueue.clear();\n''',
    '''        sessionSerial++;\n        latestInputSerial = 0L;\n        latestReplySerial++;\n        audioQueue.clear();\n''',
)

replace_once(
    client,
    '''        if (!novel.isEmpty()) {\n            // A newer accepted Realtime transcript makes any older file-reply job stale.\n            latestReplySerial++;\n            if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;\n        }\n''',
    '''        if (!novel.isEmpty()) {\n            // A newer accepted Realtime transcript makes any older file-STT result\n            // and file-reply job stale for the live overlay.\n            latestInputSerial++;\n            latestReplySerial++;\n            if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;\n        }\n''',
)

replace_once(
    client,
    '''    synchronized void submitAudio(short[] samples, int sampleRate) {\n        if (closed || samples == null || samples.length == 0) return;\n        long now = System.currentTimeMillis();\n        while (!audioQueue.isEmpty() && now - audioQueue.peekFirst().createdAtMs > MAX_AUDIO_AGE_MS) {\n            audioQueue.removeFirst();\n        }\n        while (audioQueue.size() >= MAX_AUDIO_QUEUE) audioQueue.removeFirst();\n        audioQueue.addLast(new AudioItem(samples.clone(), sampleRate, sessionSerial));\n''',
    '''    synchronized void submitAudio(short[] samples, int sampleRate) {\n        if (closed || samples == null || samples.length == 0) return;\n        long now = System.currentTimeMillis();\n        long inputSerial = ++latestInputSerial;\n        // New speech immediately invalidates reply work from older file audio.\n        latestReplySerial++;\n        while (!audioQueue.isEmpty() && now - audioQueue.peekFirst().createdAtMs > MAX_AUDIO_AGE_MS) {\n            audioQueue.removeFirst();\n        }\n        while (audioQueue.size() >= MAX_AUDIO_QUEUE) audioQueue.removeFirst();\n        audioQueue.addLast(new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial));\n''',
)

replace_once(
    client,
    '''            if (focus.isEmpty()) {\n                listener.onStatus("Слушам");\n                return;\n            }\n\n            listener.onTranscript(focus);\n''',
    '''            if (focus.isEmpty()) {\n                listener.onStatus("Слушам");\n                return;\n            }\n\n            // Keep stale speech in internal context, but never surface it over newer\n            // live input or after it has become too old to be useful on screen.\n            if (!shouldSurfaceAudioResult(item)) {\n                listener.onStatus("Слушам");\n                return;\n            }\n\n            listener.onTranscript(focus);\n''',
)

replace_once(
    client,
    '''    private synchronized boolean isCurrentSession(long serial) {\n        return !closed && serial == sessionSerial;\n    }\n''',
    '''    private synchronized boolean shouldSurfaceAudioResult(AudioItem item) {\n        long ageMs = Math.max(0L, System.currentTimeMillis() - item.createdAtMs);\n        boolean sessionMatches = !closed && item.sessionSerial == sessionSerial;\n        return FileSttFreshnessPolicy.shouldSurface(\n                item.inputSerial, latestInputSerial, ageMs, sessionMatches);\n    }\n\n    private synchronized boolean isCurrentSession(long serial) {\n        return !closed && serial == sessionSerial;\n    }\n''',
)

replace_once(
    gradle,
    '''        versionCode = 45\n        versionName = "0.44.0-newest-stt-context"\n''',
    '''        versionCode = 46\n        versionName = "0.45.0-file-stt-freshness"\n''',
)
