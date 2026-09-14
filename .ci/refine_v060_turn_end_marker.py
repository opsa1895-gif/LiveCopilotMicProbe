from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)


# Engine no longer needs to classify an emitted audio chunk as final. The explicit
# onStreamTurnEnd callback is a stronger boundary because a too-short tail may not
# produce an audio chunk at all.
engine_path = Path('app/src/main/java/com/livecopilot/micprobe/MicProbeEngine.java')
engine = engine_path.read_text()
engine = replace_once(engine,
    '        void onPcmChunk(short[] samples, int sampleRate, boolean finalChunk);',
    '        void onPcmChunk(short[] samples, int sampleRate);',
    'engine listener signature')
engine = replace_once(engine,
    '                        emitSegment(segment, voicedFramesInSegment, false);',
    '                        emitSegment(segment, voicedFramesInSegment);',
    'forced emit')
engine = replace_once(engine,
    '                        emitSegment(segment, voicedFramesInSegment, true);',
    '                        emitSegment(segment, voicedFramesInSegment);',
    'normal final emit')
engine = replace_once(engine,
    '            emitSegment(segment, voicedFramesInSegment, true);',
    '            emitSegment(segment, voicedFramesInSegment);',
    'loop exit emit')
engine = replace_once(engine,
    '''    private void emitSegment(ShortAccumulator segment, int voicedFrames, boolean finalChunk) {\n        if (voicedFrames < 4 || segment.size() < MIN_SEGMENT_SAMPLES) return;\n        listener.onPcmChunk(segment.toArray(), SAMPLE_RATE, finalChunk);\n    }''',
    '''    private void emitSegment(ShortAccumulator segment, int voicedFrames) {\n        if (voicedFrames < 4 || segment.size() < MIN_SEGMENT_SAMPLES) return;\n        listener.onPcmChunk(segment.toArray(), SAMPLE_RATE);\n    }''',
    'emitSegment signature')
engine_path.write_text(engine)


service_path = Path('app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java')
service = service_path.read_text()
old_end = '''    @Override\n    public void onStreamTurnEnd() {\n        boolean hadRealtimeBackup = realtimeBackupStreaming;\n        boolean committed = hadRealtimeBackup\n                && realtimeTurnActive\n                && realtimeTranscriber != null\n                && realtimeTranscriber.commitTurn();\n\n        realtimeTurnActive = false;\n        realtimeBackupStreaming = false;\n        if (!hadRealtimeBackup) {\n            realtimeSpeechEpoch = -1L;\n            return;\n        }\n\n        if (!committed) {\n            realtimeSpeechEpoch = -1L;\n            submitBufferedFallback();\n        } else {\n            clearFallbackTurnAudio();\n        }\n    }'''
new_end = '''    @Override\n    public void onStreamTurnEnd() {\n        boolean hadRealtimeBackup = realtimeBackupStreaming;\n        boolean committed = hadRealtimeBackup\n                && realtimeTurnActive\n                && realtimeTranscriber != null\n                && realtimeTranscriber.commitTurn();\n\n        realtimeTurnActive = false;\n        realtimeBackupStreaming = false;\n        if (!hadRealtimeBackup) {\n            realtimeSpeechEpoch = -1L;\n            finishFileTurn();\n            return;\n        }\n\n        if (!committed) {\n            realtimeSpeechEpoch = -1L;\n            submitBufferedFallback();\n            finishFileTurn();\n        } else {\n            clearFallbackTurnAudio();\n        }\n    }'''
service = replace_once(service, old_end, new_end, 'stream turn end marker')
service = replace_once(service,
    '    public void onPcmChunk(short[] samples, int sampleRate, boolean finalChunk) {',
    '    public void onPcmChunk(short[] samples, int sampleRate) {',
    'service chunk signature')
service = replace_once(service,
    'if (prepared.length > 0) aiClient.submitAudio(prepared, sampleRate, fileSpeechEpoch, finalChunk);',
    'if (prepared.length > 0) aiClient.submitAudio(prepared, sampleRate, fileSpeechEpoch);',
    'service chunk submit')
service = replace_once(service,
    'if (prepared.length > 0) aiClient.submitAudio(prepared, rate, fileSpeechEpoch, true);',
    'if (prepared.length > 0) aiClient.submitAudio(prepared, rate, fileSpeechEpoch);',
    'buffered fallback submit')
insert_anchor = '''    @Override\n    public void onTranscript(long sessionSerial, long inputSerial, String transcript) {'''
finish_helper = '''    private void finishFileTurn() {\n        if (aiClient == null || engine == null || !engine.isRunning()) return;\n        aiClient.finishAudioTurn(fileSpeechEpoch);\n    }\n\n'''
if service.count(insert_anchor) != 1:
    raise SystemExit('finish helper anchor mismatch')
service = service.replace(insert_anchor, finish_helper + insert_anchor, 1)
service_path.write_text(service)


client_path = Path('app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java')
client = client_path.read_text()

# Replace AudioItem block and add a FIFO turn-end marker.
a_start = client.index('    private static final class AudioItem {')
a_end = client.index('    private static final class ReplyJob {', a_start)
client = client[:a_start] + '''    private static final class AudioItem {\n        final short[] samples;\n        final int sampleRate;\n        final long createdAtMs;\n        final long sessionSerial;\n        final long inputSerial;\n\n        AudioItem(short[] samples, int sampleRate, long sessionSerial, long inputSerial) {\n            this.samples = samples;\n            this.sampleRate = sampleRate;\n            this.createdAtMs = System.currentTimeMillis();\n            this.sessionSerial = sessionSerial;\n            this.inputSerial = inputSerial;\n        }\n    }\n\n    private static final class TurnEndItem {\n        final long sessionSerial;\n        final long inputSerial;\n        final long createdAtMs;\n\n        TurnEndItem(long sessionSerial, long inputSerial) {\n            this.sessionSerial = sessionSerial;\n            this.inputSerial = inputSerial;\n            this.createdAtMs = System.currentTimeMillis();\n        }\n    }\n\n''' + client[a_end:]

# Replace submitAudio and add finishAudioTurn marker enqueue.
s_start = client.index('    synchronized void submitAudio(')
s_end = client.index('    private void processAudioIfFresh(', s_start)
client = client[:s_start] + '''    synchronized void submitAudio(short[] samples, int sampleRate, long inputSerial) {\n        if (closed || samples == null || samples.length == 0) return;\n        if (inputSerial <= 0L || inputSerial != latestInputSerial) return;\n        // Forced chunks from one long utterance stay FIFO and share the same turn\n        // token. They can enrich transcript/context but never generate a reply here.\n        latestReplySerial++;\n        replyHttp.cancelAll();\n        AudioItem item = new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial);\n        audioExecutor.execute(() -> processAudioIfFresh(item));\n    }\n\n    synchronized void finishAudioTurn(long inputSerial) {\n        if (closed || inputSerial <= 0L || inputSerial != latestInputSerial) return;\n        // This marker is queued behind every already-submitted STT chunk. It therefore\n        // finalizes the complete speech turn even when the final audio tail was too\n        // short/quiet for MicProbeEngine.emitSegment().\n        TurnEndItem end = new TurnEndItem(sessionSerial, inputSerial);\n        audioExecutor.execute(() -> finishAudioTurnIfFresh(end));\n    }\n\n''' + client[s_end:]

# Replace processAudio and the previous final-chunk helpers.
p_start = client.index('    private void processAudio(AudioItem item) {')
p_end = client.index('    private synchronized void prepareContextFor(', p_start)
new_process = '''    private void processAudio(AudioItem item) {\n        String key = SecretStore.loadApiKey(context);\n        if (key.isEmpty()) {\n            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Няма API key");\n            return;\n        }\n\n        try {\n            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Разпознавам…");\n            String raw = transcribe(key, item);\n            if (!isCurrentSession(item.sessionSerial)) return;\n            if (TranscriptQualityPolicy.isLowQuality(raw)) {\n                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");\n                return;\n            }\n\n            long now = System.currentTimeMillis();\n            String focus;\n            synchronized (this) {\n                // File STT may finish after newer speech has already started. Keep\n                // context and the pending whole-turn focus behind the same freshness\n                // gate and monitor.\n                if (!isProcessableAudioResultLocked(item)) {\n                    focus = "";\n                } else {\n                    String useful = removeRecentSelfEcho(raw, now);\n                    if (useful.isEmpty()) {\n                        focus = "";\n                    } else {\n                        boolean topicShift = isStrongTopicShift(useful);\n                        prepareContextFor(useful, now);\n                        focus = commitTranscript(useful, now);\n                        if (!focus.isEmpty()) {\n                            if (fileTurnSerial != item.inputSerial) {\n                                fileTurnSerial = item.inputSerial;\n                                fileTurnFocus = "";\n                            }\n                            fileTurnFocus = FileTurnReplyPolicy.appendFocus(\n                                    fileTurnFocus, focus, topicShift);\n                            if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;\n                        }\n                    }\n                }\n            }\n            if (focus.isEmpty()) {\n                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");\n                return;\n            }\n\n            if (!shouldSurfaceAudioResult(item)) {\n                listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");\n                return;\n            }\n\n            listener.onTranscript(item.sessionSerial, item.inputSerial, focus);\n            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");\n        } catch (Throwable ignored) {\n            // A failed chunk does not destroy focus already accumulated from earlier\n            // chunks; the queued turn-end marker may still produce a useful reply.\n            if (shouldSurfaceAudioResult(item)) {\n                listener.onStatus(item.sessionSerial, item.inputSerial, false, "AI връзката прекъсна");\n            }\n        }\n    }\n\n    private void finishAudioTurnIfFresh(TurnEndItem end) {\n        String turnFocus;\n        long reference;\n        long now = System.currentTimeMillis();\n        synchronized (this) {\n            // New speech/pause/session reset changes the active serial and leaves this\n            // queued marker unable to consume or reply for the newer turn.\n            if (!FileTurnReplyPolicy.canFinalize(end.inputSerial, fileTurnSerial)\n                    || closed\n                    || end.sessionSerial != sessionSerial\n                    || end.inputSerial != latestInputSerial) return;\n\n            turnFocus = fileTurnFocus;\n            fileTurnSerial = -1L;\n            fileTurnFocus = "";\n            if (turnFocus.isEmpty() || !isTurnEndSurfaceableLocked(end, now)) return;\n            reference = lastReplyAtMs > 0L ? lastReplyAtMs : firstSpeechAtMs;\n        }\n\n        boolean actionable = isActionable(turnFocus);\n        boolean engagement = !actionable && reference > 0L && now - reference >= ENGAGEMENT_GAP_MS;\n        if (!(actionable || engagement)\n                || !queueReplyIfFresh(end, turnFocus, engagement)) {\n            listener.onStatus(end.sessionSerial, end.inputSerial, false, "Слушам");\n        }\n    }\n\n    private boolean isTurnEndSurfaceableLocked(TurnEndItem end, long now) {\n        long ageMs = Math.max(0L, now - end.createdAtMs);\n        boolean sessionMatches = !closed && end.sessionSerial == sessionSerial;\n        return FileSttFreshnessPolicy.shouldSurface(\n                end.inputSerial, latestInputSerial, ageMs, sessionMatches);\n    }\n\n'''
client = client[:p_start] + new_process + client[p_end:]

# Reply creation now rechecks the turn-end marker, not an individual audio chunk.
q_start = client.index('    private boolean queueReplyIfFresh(')
q_end = client.index('    private void processReply(', q_start)
client = client[:q_start] + '''    private boolean queueReplyIfFresh(TurnEndItem end, String focus, boolean engagement) {\n        ReplyJob job;\n        synchronized (this) {\n            // Re-check end-marker freshness atomically with reply-job creation.\n            if (!isTurnEndSurfaceableLocked(end, System.currentTimeMillis())) return false;\n            long serial = ++latestReplySerial;\n            replyHttp.cancelAll();\n            job = new ReplyJob(serial, sessionSerial, contextTextLocked(), focus, lastDirectReply, engagement);\n        }\n        replyExecutor.execute(() -> processReply(job));\n        return true;\n    }\n\n''' + client[q_end:]
client_path.write_text(client)


policy_path = Path('app/src/main/java/com/livecopilot/micprobe/FileTurnReplyPolicy.java')
policy = policy_path.read_text()
policy = replace_once(policy,
    '''    static boolean isFinalForTurn(boolean finalChunk, long itemSerial, long activeTurnSerial) {\n        return finalChunk && itemSerial > 0L && itemSerial == activeTurnSerial;\n    }''',
    '''    static boolean canFinalize(long endSerial, long activeTurnSerial) {\n        return endSerial > 0L && endSerial == activeTurnSerial;\n    }''',
    'turn end policy')
policy_path.write_text(policy)


test_path = Path('app/src/test/java/com/livecopilot/micprobe/FileTurnReplyPolicyTest.java')
test = test_path.read_text()
old_tests = '''    @Test\n    public void forcedChunkCannotFinalizeSpeechTurn() {\n        assertFalse(FileTurnReplyPolicy.isFinalForTurn(false, 8L, 8L));\n    }\n\n    @Test\n    public void onlyFinalChunkFromCurrentTurnCanFinalize() {\n        assertTrue(FileTurnReplyPolicy.isFinalForTurn(true, 9L, 9L));\n        assertFalse(FileTurnReplyPolicy.isFinalForTurn(true, 8L, 9L));\n        assertFalse(FileTurnReplyPolicy.isFinalForTurn(true, -1L, -1L));\n    }'''
new_tests = '''    @Test\n    public void turnEndMarkerFinalizesOnlyMatchingCurrentTurn() {\n        assertTrue(FileTurnReplyPolicy.canFinalize(9L, 9L));\n        assertFalse(FileTurnReplyPolicy.canFinalize(8L, 9L));\n        assertFalse(FileTurnReplyPolicy.canFinalize(-1L, -1L));\n    }'''
test = replace_once(test, old_tests, new_tests, 'turn end tests')
test_path.write_text(test)
