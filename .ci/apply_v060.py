from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)


# Engine: distinguish forced long-turn chunks from the final chunk of a speech turn.
engine_path = Path('app/src/main/java/com/livecopilot/micprobe/MicProbeEngine.java')
engine = engine_path.read_text()
engine = replace_once(
    engine,
    '        void onPcmChunk(short[] samples, int sampleRate);',
    '        void onPcmChunk(short[] samples, int sampleRate, boolean finalChunk);',
    'listener signature')
engine = replace_once(
    engine,
    '                        emitSegment(segment, voicedFramesInSegment);\n                        short[] overlap = segment.tail(OVERLAP_SAMPLES);',
    '                        emitSegment(segment, voicedFramesInSegment, false);\n                        short[] overlap = segment.tail(OVERLAP_SAMPLES);',
    'forced split')
engine = replace_once(
    engine,
    '                        emitSegment(segment, voicedFramesInSegment);\n                        listener.onStreamTurnEnd();',
    '                        emitSegment(segment, voicedFramesInSegment, true);\n                        listener.onStreamTurnEnd();',
    'normal turn end')
engine = replace_once(
    engine,
    '            emitSegment(segment, voicedFramesInSegment);\n        }\n        if (speaking) {',
    '            emitSegment(segment, voicedFramesInSegment, true);\n        }\n        if (speaking) {',
    'loop exit turn end')
engine = replace_once(
    engine,
    '''    private void emitSegment(ShortAccumulator segment, int voicedFrames) {\n        if (voicedFrames < 4 || segment.size() < MIN_SEGMENT_SAMPLES) return;\n        listener.onPcmChunk(segment.toArray(), SAMPLE_RATE);\n    }''',
    '''    private void emitSegment(ShortAccumulator segment, int voicedFrames, boolean finalChunk) {\n        if (voicedFrames < 4 || segment.size() < MIN_SEGMENT_SAMPLES) return;\n        listener.onPcmChunk(segment.toArray(), SAMPLE_RATE, finalChunk);\n    }''',
    'emitSegment')
engine_path.write_text(engine)


# Service: pass the final-chunk bit into file STT; a contiguous Realtime recovery is
# submitted only at turn end, so it is always final.
service_path = Path('app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java')
service = service_path.read_text()
service = replace_once(
    service,
    '    public void onPcmChunk(short[] samples, int sampleRate) {',
    '    public void onPcmChunk(short[] samples, int sampleRate, boolean finalChunk) {',
    'service chunk signature')
service = replace_once(
    service,
    'if (prepared.length > 0) aiClient.submitAudio(prepared, sampleRate, fileSpeechEpoch);',
    'if (prepared.length > 0) aiClient.submitAudio(prepared, sampleRate, fileSpeechEpoch, finalChunk);',
    'service chunk submit')
service = replace_once(
    service,
    'if (prepared.length > 0) aiClient.submitAudio(prepared, rate, fileSpeechEpoch);',
    'if (prepared.length > 0) aiClient.submitAudio(prepared, rate, fileSpeechEpoch, true);',
    'buffered fallback submit')
service_path.write_text(service)


# Client: aggregate novel text across forced chunks, but only decide/generate a reply
# once the final chunk for that speech-turn token has finished.
client_path = Path('app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java')
client = client_path.read_text()
client = replace_once(
    client,
    '''        final long sessionSerial;\n        final long inputSerial;\n\n        AudioItem(short[] samples, int sampleRate, long sessionSerial, long inputSerial) {\n            this.samples = samples;\n            this.sampleRate = sampleRate;\n            this.createdAtMs = System.currentTimeMillis();\n            this.sessionSerial = sessionSerial;\n            this.inputSerial = inputSerial;\n        }''',
    '''        final long sessionSerial;\n        final long inputSerial;\n        final boolean finalChunk;\n\n        AudioItem(short[] samples, int sampleRate, long sessionSerial, long inputSerial, boolean finalChunk) {\n            this.samples = samples;\n            this.sampleRate = sampleRate;\n            this.createdAtMs = System.currentTimeMillis();\n            this.sessionSerial = sessionSerial;\n            this.inputSerial = inputSerial;\n            this.finalChunk = finalChunk;\n        }''',
    'audio item final flag')
client = replace_once(
    client,
    '    private long latestReplySerial;\n    private long lastReplyAtMs;',
    '    private long latestReplySerial;\n    private long fileTurnSerial = -1L;\n    private String fileTurnFocus = "";\n    private long lastReplyAtMs;',
    'turn focus fields')
client = replace_once(
    client,
    '        latestInputSerial = 0L;\n        latestReplySerial++;\n        recentTurns.clear();',
    '        latestInputSerial = 0L;\n        latestReplySerial++;\n        fileTurnSerial = -1L;\n        fileTurnFocus = "";\n        recentTurns.clear();',
    'reset pending turn')
client = replace_once(
    client,
    '''        latestInputSerial++;\n        latestReplySerial++;\n        audioHttp.cancelAll();\n        replyHttp.cancelAll();\n        return latestInputSerial;''',
    '''        latestInputSerial++;\n        latestReplySerial++;\n        fileTurnSerial = latestInputSerial;\n        fileTurnFocus = "";\n        audioHttp.cancelAll();\n        replyHttp.cancelAll();\n        return latestInputSerial;''',
    'new speech pending turn')
client = replace_once(
    client,
    '''        latestInputSerial++;\n        latestReplySerial++;\n        audioHttp.cancelAll();\n        replyHttp.cancelAll();\n    }\n\n    synchronized boolean isTranscriptCallbackCurrent''',
    '''        latestInputSerial++;\n        latestReplySerial++;\n        fileTurnSerial = -1L;\n        fileTurnFocus = "";\n        audioHttp.cancelAll();\n        replyHttp.cancelAll();\n    }\n\n    synchronized boolean isTranscriptCallbackCurrent''',
    'invalidate pending turn')
client = replace_once(
    client,
    '''            latestInputSerial++;\n            latestReplySerial++;\n            audioHttp.cancelAll();\n            replyHttp.cancelAll();''',
    '''            latestInputSerial++;\n            latestReplySerial++;\n            fileTurnSerial = -1L;\n            fileTurnFocus = "";\n            audioHttp.cancelAll();\n            replyHttp.cancelAll();''',
    'realtime invalidates pending file turn')
client = replace_once(
    client,
    '''    synchronized void submitAudio(short[] samples, int sampleRate, long inputSerial) {\n        if (closed || samples == null || samples.length == 0) return;\n        if (inputSerial <= 0L || inputSerial != latestInputSerial) return;\n        // Chunks from one long utterance must stay in FIFO order and share the same\n        // turn token. A later chunk may supersede reply generation, but it must not\n        // cancel transcription of an earlier chunk from the same speech turn.\n        latestReplySerial++;\n        replyHttp.cancelAll();\n        AudioItem item = new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial);\n        audioExecutor.execute(() -> processAudioIfFresh(item));\n    }''',
    '''    synchronized void submitAudio(short[] samples, int sampleRate, long inputSerial, boolean finalChunk) {\n        if (closed || samples == null || samples.length == 0) return;\n        if (inputSerial <= 0L || inputSerial != latestInputSerial) return;\n        // Chunks from one long utterance stay FIFO and share the same turn token.\n        // Forced chunks can enrich transcript/context, but only finalChunk may create\n        // a reply for the completed utterance.\n        latestReplySerial++;\n        replyHttp.cancelAll();\n        AudioItem item = new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial, finalChunk);\n        audioExecutor.execute(() -> processAudioIfFresh(item));\n    }''',
    'submitAudio final flag')

start = client.index('    private void processAudio(AudioItem item) {')
end = client.index('    private synchronized void prepareContextFor(', start)
new_process = '''    private void processAudio(AudioItem item) {\n        String key = SecretStore.loadApiKey(context);\n        if (key.isEmpty()) {\n            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Няма API key");\n            return;\n        }\n\n        try {\n            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Разпознавам…");\n            String raw = transcribe(key, item);\n            if (!isCurrentSession(item.sessionSerial)) return;\n            long now = System.currentTimeMillis();\n            if (TranscriptQualityPolicy.isLowQuality(raw)) {\n                if (!finishFileTurnReplyIfNeeded(item, now)) {\n                    listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");\n                }\n                return;\n            }\n\n            String focus;\n            String turnFocus;\n            synchronized (this) {\n                // File STT may finish after newer speech has already started. Check\n                // freshness while holding the same monitor used for conversation\n                // mutations so stale audio cannot alter context or pending turn focus.\n                if (!isProcessableAudioResultLocked(item)) {\n                    focus = "";\n                    turnFocus = "";\n                } else {\n                    String useful = removeRecentSelfEcho(raw, now);\n                    if (useful.isEmpty()) {\n                        focus = "";\n                    } else {\n                        boolean topicShift = isStrongTopicShift(useful);\n                        prepareContextFor(useful, now);\n                        focus = commitTranscript(useful, now);\n                        if (!focus.isEmpty()) {\n                            if (fileTurnSerial != item.inputSerial) {\n                                fileTurnSerial = item.inputSerial;\n                                fileTurnFocus = "";\n                            }\n                            fileTurnFocus = FileTurnReplyPolicy.appendFocus(\n                                    fileTurnFocus, focus, topicShift);\n                            if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;\n                        }\n                    }\n                    turnFocus = consumeFileTurnFocusIfFinalLocked(item);\n                }\n            }\n\n            boolean surface = shouldSurfaceAudioResult(item);\n            if (!focus.isEmpty() && surface) {\n                listener.onTranscript(item.sessionSerial, item.inputSerial, focus);\n            }\n\n            // Forced chunks are useful for live transcript/context only. Waiting for\n            // finalChunk prevents a reply from landing while the speaker is still\n            // continuing the same long utterance.\n            if (!item.finalChunk) {\n                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");\n                return;\n            }\n            if (!surface || turnFocus.isEmpty()\n                    || !queueTurnReplyIfNeeded(item, turnFocus, now)) {\n                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");\n            }\n        } catch (Throwable ignored) {\n            long now = System.currentTimeMillis();\n            // If only the final STT chunk failed, earlier successfully transcribed\n            // chunks from this same current turn can still drive a useful final reply.\n            if (item.finalChunk && shouldSurfaceAudioResult(item)\n                    && finishFileTurnReplyIfNeeded(item, now)) return;\n            // Cancellation caused by newer speech is expected and must not surface\n            // as a connection error for the obsolete audio item.\n            if (shouldSurfaceAudioResult(item)) {\n                listener.onStatus(item.sessionSerial, item.inputSerial, false, "AI връзката прекъсна");\n            }\n        }\n    }\n\n    private boolean finishFileTurnReplyIfNeeded(AudioItem item, long now) {\n        String turnFocus;\n        synchronized (this) {\n            turnFocus = consumeFileTurnFocusIfFinalLocked(item);\n        }\n        return !turnFocus.isEmpty()\n                && shouldSurfaceAudioResult(item)\n                && queueTurnReplyIfNeeded(item, turnFocus, now);\n    }\n\n    private boolean queueTurnReplyIfNeeded(AudioItem item, String turnFocus, long now) {\n        if (turnFocus == null || turnFocus.trim().isEmpty() || !shouldSurfaceAudioResult(item)) {\n            return false;\n        }\n        boolean actionable = isActionable(turnFocus);\n        long reference;\n        synchronized (this) {\n            reference = lastReplyAtMs > 0L ? lastReplyAtMs : firstSpeechAtMs;\n        }\n        boolean engagement = !actionable && reference > 0L && now - reference >= ENGAGEMENT_GAP_MS;\n        return (actionable || engagement) && queueReplyIfFresh(item, turnFocus, engagement);\n    }\n\n    private String consumeFileTurnFocusIfFinalLocked(AudioItem item) {\n        if (!FileTurnReplyPolicy.isFinalForTurn(\n                item.finalChunk, item.inputSerial, fileTurnSerial)) return "";\n        String focus = fileTurnFocus;\n        fileTurnSerial = -1L;\n        fileTurnFocus = "";\n        return focus;\n    }\n\n'''
client = client[:start] + new_process + client[end:]
client_path.write_text(client)


# Pure policy keeps turn-boundary and focus-compaction behavior unit-testable.
policy_path = Path('app/src/main/java/com/livecopilot/micprobe/FileTurnReplyPolicy.java')
policy_path.write_text('''package com.livecopilot.micprobe;\n\nfinal class FileTurnReplyPolicy {\n    static final int MAX_FOCUS_CHARS = 700;\n    private static final int HEAD_CHARS = 260;\n\n    private FileTurnReplyPolicy() {}\n\n    static boolean isFinalForTurn(boolean finalChunk, long itemSerial, long activeTurnSerial) {\n        return finalChunk && itemSerial > 0L && itemSerial == activeTurnSerial;\n    }\n\n    static String appendFocus(String current, String novel, boolean topicShift) {\n        String before = clean(current);\n        String addition = clean(novel);\n        if (addition.isEmpty()) return topicShift ? "" : before;\n\n        String combined = topicShift || before.isEmpty()\n                ? addition\n                : before + " " + addition;\n        if (combined.length() <= MAX_FOCUS_CHARS) return combined;\n\n        int tailChars = MAX_FOCUS_CHARS - HEAD_CHARS - 3;\n        String head = combined.substring(0, HEAD_CHARS).trim();\n        String tail = combined.substring(combined.length() - tailChars).trim();\n        return head + " … " + tail;\n    }\n\n    private static String clean(String value) {\n        if (value == null) return "";\n        return value.replace('\\n', ' ').replace('\\r', ' ')\n                .replaceAll("\\\\s+", " ").trim();\n    }\n}\n''')


test_path = Path('app/src/test/java/com/livecopilot/micprobe/FileTurnReplyPolicyTest.java')
test_path.write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\nimport static org.junit.Assert.assertEquals;\n\npublic class FileTurnReplyPolicyTest {\n    @Test\n    public void forcedChunkCannotFinalizeSpeechTurn() {\n        assertFalse(FileTurnReplyPolicy.isFinalForTurn(false, 8L, 8L));\n    }\n\n    @Test\n    public void onlyFinalChunkFromCurrentTurnCanFinalize() {\n        assertTrue(FileTurnReplyPolicy.isFinalForTurn(true, 9L, 9L));\n        assertFalse(FileTurnReplyPolicy.isFinalForTurn(true, 8L, 9L));\n        assertFalse(FileTurnReplyPolicy.isFinalForTurn(true, -1L, -1L));\n    }\n\n    @Test\n    public void focusAccumulatesAcrossForcedChunks() {\n        String first = FileTurnReplyPolicy.appendFocus("", "Какво мислиш за", false);\n        String complete = FileTurnReplyPolicy.appendFocus(first, "това предложение?", false);\n        assertEquals("Какво мислиш за това предложение?", complete);\n    }\n\n    @Test\n    public void topicShiftDropsEarlierTurnFocus() {\n        String focus = FileTurnReplyPolicy.appendFocus("стар въпрос за цена", "между другото нов въпрос", true);\n        assertEquals("между другото нов въпрос", focus);\n    }\n\n    @Test\n    public void longFocusKeepsBeginningAndLatestTail() {\n        StringBuilder middle = new StringBuilder("начало ");\n        for (int i = 0; i < 900; i++) middle.append('x');\n        middle.append(" край");\n        String compact = FileTurnReplyPolicy.appendFocus("", middle.toString(), false);\n        assertTrue(compact.length() <= FileTurnReplyPolicy.MAX_FOCUS_CHARS);\n        assertTrue(compact.startsWith("начало"));\n        assertTrue(compact.endsWith("край"));\n    }\n}\n''')


gradle_path = Path('app/build.gradle.kts')
gradle = gradle_path.read_text()
gradle = replace_once(
    gradle,
    '''        versionCode = 60\n        versionName = "0.59.0-preserve-long-file-turns"''',
    '''        versionCode = 61\n        versionName = "0.60.0-final-turn-replies"''',
    'version')
gradle_path.write_text(gradle)
