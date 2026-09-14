from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)


client_path = Path('app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java')
client = client_path.read_text()

client = replace_once(
    client,
    '    private final LatestWinsExecutor audioExecutor = new LatestWinsExecutor(1);',
    '    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();',
    'audio executor')

client = replace_once(
    client,
    '''    synchronized void noteNewSpeech() {\n        if (closed) return;\n        // Invalidate in-flight file STT/reply work as soon as a newer utterance starts,\n        // rather than waiting for its final transcript.\n        latestInputSerial++;\n        latestReplySerial++;\n        audioHttp.cancelAll();\n        replyHttp.cancelAll();\n    }''',
    '''    synchronized long noteNewSpeech() {\n        if (closed) return -1L;\n        // One input serial represents the whole speech turn. Forced 6s file-STT\n        // chunks from that turn share the token; only genuinely newer speech\n        // advances it and cancels the previous turn's active transcription.\n        latestInputSerial++;\n        latestReplySerial++;\n        audioHttp.cancelAll();\n        replyHttp.cancelAll();\n        return latestInputSerial;\n    }''',
    'noteNewSpeech')

client = replace_once(
    client,
    '''    synchronized void submitAudio(short[] samples, int sampleRate) {\n        if (closed || samples == null || samples.length == 0) return;\n        long inputSerial = ++latestInputSerial;\n        // New file audio supersedes any older in-flight transcription as well as\n        // reply work derived from older audio. LatestWinsExecutor also keeps only\n        // one pending task, so bursty fallback audio cannot build a stale backlog.\n        latestReplySerial++;\n        audioHttp.cancelAll();\n        replyHttp.cancelAll();\n        AudioItem item = new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial);\n        audioExecutor.execute(() -> processAudioIfFresh(item));\n    }''',
    '''    synchronized void submitAudio(short[] samples, int sampleRate, long inputSerial) {\n        if (closed || samples == null || samples.length == 0) return;\n        if (inputSerial <= 0L || inputSerial != latestInputSerial) return;\n        // Chunks from one long utterance must stay in FIFO order and share the same\n        // turn token. A later chunk may supersede reply generation, but it must not\n        // cancel transcription of an earlier chunk from the same speech turn.\n        latestReplySerial++;\n        replyHttp.cancelAll();\n        AudioItem item = new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial);\n        audioExecutor.execute(() -> processAudioIfFresh(item));\n    }''',
    'submitAudio')

client = replace_once(
    client,
    '''    private void processAudioIfFresh(AudioItem item) {\n        // A queued task may have been superseded before the worker became free.\n        // Reject it before API-key loading, status callbacks, or network work.\n        if (item == null || !shouldSurfaceAudioResult(item)) return;\n        processAudio(item);\n    }''',
    '''    private void processAudioIfFresh(AudioItem item) {\n        // Old turns are rejected before API-key loading/status/network work, while\n        // multiple chunks from the current long turn remain processable in FIFO order.\n        if (item == null || !shouldProcessAudioResult(item)) return;\n        processAudio(item);\n    }''',
    'processAudioIfFresh')

client = replace_once(
    client,
    '                if (!isFreshAudioResultLocked(item)) {',
    '                if (!isProcessableAudioResultLocked(item)) {',
    'atomic context freshness')

client = replace_once(
    client,
    '            if (!isFreshAudioResultLocked(item)) return false;',
    '            if (!isSurfaceableAudioResultLocked(item)) return false;',
    'reply surface freshness')

old_helpers = '''    private synchronized boolean shouldSurfaceAudioResult(AudioItem item) {\n        return isFreshAudioResultLocked(item);\n    }\n\n    private boolean isFreshAudioResultLocked(AudioItem item) {\n        long ageMs = Math.max(0L, System.currentTimeMillis() - item.createdAtMs);\n        boolean sessionMatches = !closed && item.sessionSerial == sessionSerial;\n        return FileSttFreshnessPolicy.shouldAccept(\n                item.inputSerial, latestInputSerial, ageMs, sessionMatches);\n    }'''
new_helpers = '''    private synchronized boolean shouldProcessAudioResult(AudioItem item) {\n        return isProcessableAudioResultLocked(item);\n    }\n\n    private synchronized boolean shouldSurfaceAudioResult(AudioItem item) {\n        return isSurfaceableAudioResultLocked(item);\n    }\n\n    private boolean isProcessableAudioResultLocked(AudioItem item) {\n        long ageMs = Math.max(0L, System.currentTimeMillis() - item.createdAtMs);\n        boolean sessionMatches = !closed && item.sessionSerial == sessionSerial;\n        return FileSttFreshnessPolicy.shouldAccept(\n                item.inputSerial, latestInputSerial, ageMs, sessionMatches);\n    }\n\n    private boolean isSurfaceableAudioResultLocked(AudioItem item) {\n        long ageMs = Math.max(0L, System.currentTimeMillis() - item.createdAtMs);\n        boolean sessionMatches = !closed && item.sessionSerial == sessionSerial;\n        return FileSttFreshnessPolicy.shouldSurface(\n                item.inputSerial, latestInputSerial, ageMs, sessionMatches);\n    }'''
client = replace_once(client, old_helpers, new_helpers, 'audio freshness helpers')

start = client.index('    private String transcribe(String key, AudioItem item) throws Exception {')
end = client.index('    private String primaryReply(', start)
network_section = client[start:end]
count = network_section.count('shouldSurfaceAudioResult(item)')
if count != 6:
    raise SystemExit(f'network process freshness: expected 6 matches, got {count}')
network_section = network_section.replace('shouldSurfaceAudioResult(item)', 'shouldProcessAudioResult(item)')
client = client[:start] + network_section + client[end:]

client_path.write_text(client)

service_path = Path('app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java')
service = service_path.read_text()
service = replace_once(
    service,
    '    private volatile long realtimeSpeechEpoch = -1L;\n',
    '    private volatile long realtimeSpeechEpoch = -1L;\n    private volatile long fileSpeechEpoch = -1L;\n',
    'file speech epoch field')
service = replace_once(
    service,
    '        if (aiClient != null) aiClient.noteNewSpeech();\n',
    '        fileSpeechEpoch = aiClient != null ? aiClient.noteNewSpeech() : -1L;\n',
    'turn start file epoch')
if service.count('aiClient.submitAudio(prepared, sampleRate);') != 1:
    raise SystemExit('onPcmChunk submitAudio count mismatch')
service = service.replace(
    'if (prepared.length > 0) aiClient.submitAudio(prepared, sampleRate);',
    'if (prepared.length > 0) aiClient.submitAudio(prepared, sampleRate, fileSpeechEpoch);',
    1)
if service.count('aiClient.submitAudio(prepared, rate);') != 1:
    raise SystemExit('buffered fallback submitAudio count mismatch')
service = service.replace(
    'if (prepared.length > 0) aiClient.submitAudio(prepared, rate);',
    'if (prepared.length > 0) aiClient.submitAudio(prepared, rate, fileSpeechEpoch);',
    1)
service = replace_once(
    service,
    '''            if (aiClient != null) aiClient.invalidatePendingWork();\n            engine.stop("user_paused");''',
    '''            if (aiClient != null) aiClient.invalidatePendingWork();\n            fileSpeechEpoch = -1L;\n            engine.stop("user_paused");''',
    'pause file epoch')
service = replace_once(
    service,
    '''            realtimePartial = "";\n            realtimeSpeechEpoch = -1L;\n            lastRealtimeTurnSerial = 0L;''',
    '''            realtimePartial = "";\n            realtimeSpeechEpoch = -1L;\n            fileSpeechEpoch = -1L;\n            lastRealtimeTurnSerial = 0L;''',
    'reset file epoch')
service_path.write_text(service)

policy_path = Path('app/src/main/java/com/livecopilot/micprobe/FileSttFreshnessPolicy.java')
policy_path.write_text('''package com.livecopilot.micprobe;\n\nfinal class FileSttFreshnessPolicy {\n    static final long MAX_SURFACE_AGE_MS = 10_000L;\n    static final long MAX_PROCESS_AGE_MS = 30_000L;\n\n    private FileSttFreshnessPolicy() {}\n\n    static boolean shouldAccept(long itemSerial, long latestSerial, long ageMs, boolean sessionMatches) {\n        return sessionMatches\n                && itemSerial > 0L\n                && itemSerial == latestSerial\n                && ageMs >= 0L\n                && ageMs <= MAX_PROCESS_AGE_MS;\n    }\n\n    static boolean shouldSurface(long itemSerial, long latestSerial, long ageMs, boolean sessionMatches) {\n        return shouldAccept(itemSerial, latestSerial, ageMs, sessionMatches)\n                && ageMs <= MAX_SURFACE_AGE_MS;\n    }\n}\n''')

test_path = Path('app/src/test/java/com/livecopilot/micprobe/FileSttFreshnessPolicyTest.java')
test = test_path.read_text()
insert = '''\n    @Test\n    public void sameSpeechTurnChunksShareFreshnessToken() {\n        long turnSerial = 21L;\n        assertTrue(FileSttFreshnessPolicy.shouldAccept(turnSerial, turnSerial, 500L, true));\n        assertTrue(FileSttFreshnessPolicy.shouldAccept(turnSerial, turnSerial, 6_500L, true));\n        assertFalse(FileSttFreshnessPolicy.shouldAccept(turnSerial, turnSerial + 1L, 6_501L, true));\n    }\n\n    @Test\n    public void slowCurrentTurnCanEnrichContextWithoutSurfacingLive() {\n        long turnSerial = 22L;\n        long ageMs = FileSttFreshnessPolicy.MAX_SURFACE_AGE_MS + 1L;\n        assertTrue(FileSttFreshnessPolicy.shouldAccept(turnSerial, turnSerial, ageMs, true));\n        assertFalse(FileSttFreshnessPolicy.shouldSurface(turnSerial, turnSerial, ageMs, true));\n    }\n\n    @Test\n    public void veryOldCurrentTurnStopsProcessing() {\n        long turnSerial = 23L;\n        assertFalse(FileSttFreshnessPolicy.shouldAccept(\n                turnSerial, turnSerial, FileSttFreshnessPolicy.MAX_PROCESS_AGE_MS + 1L, true));\n    }\n'''
if not test.endswith('}\n'):
    raise SystemExit('unexpected freshness test ending')
test_path.write_text(test[:-2] + insert + '}\n')

gradle_path = Path('app/build.gradle.kts')
gradle = gradle_path.read_text()
gradle = replace_once(
    gradle,
    '''        versionCode = 59\n        versionName = "0.58.0-latest-file-stt-queue"''',
    '''        versionCode = 60\n        versionName = "0.59.0-preserve-long-file-turns"''',
    'version')
gradle_path.write_text(gradle)
