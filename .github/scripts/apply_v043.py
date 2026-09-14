from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:180]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


client = "app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java"
service = "app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java"
gradle = "app/build.gradle.kts"

replace_once(
    client,
    '''        firstSpeechAtMs = 0L;\n        lastTranscriptAtMs = 0L;\n    }\n\n    synchronized void submitAudio(short[] samples, int sampleRate) {\n''',
    '''        firstSpeechAtMs = 0L;\n        lastTranscriptAtMs = 0L;\n    }\n\n    synchronized void rememberAcceptedTranscript(String transcript) {\n        if (closed) return;\n        String value = clean(transcript);\n        if (value.length() < 2) return;\n\n        long now = System.currentTimeMillis();\n        prepareContextFor(value, now);\n        String novel = commitTranscript(value, now);\n        if (!novel.isEmpty()) {\n            // A newer accepted Realtime transcript makes any older file-reply job stale.\n            latestReplySerial++;\n            if (firstSpeechAtMs == 0L) firstSpeechAtMs = now;\n        }\n    }\n\n    synchronized void rememberShownReplies(Replies replies, long shownAtMs) {\n        if (closed || replies == null) return;\n\n        String direct = clean(replies.direct);\n        String sarcastic = clean(replies.sarcastic);\n        String funny = clean(replies.funny);\n        String calm = clean(replies.calm);\n        if (direct.isEmpty() && sarcastic.isEmpty() && funny.isEmpty() && calm.isEmpty()) return;\n\n        // External semantic replies must also invalidate older file-reply jobs and\n        // seed file-STT self-echo suppression with what the user actually saw.\n        latestReplySerial++;\n        if (!direct.isEmpty()) lastDirectReply = shorten(direct, 180);\n\n        boolean primaryOnly = !direct.isEmpty()\n                && sarcastic.isEmpty() && funny.isEmpty() && calm.isEmpty();\n        if (primaryOnly) {\n            lastSarcasticReply = \"\";\n            lastFunnyReply = \"\";\n            lastCalmReply = \"\";\n        } else {\n            if (!sarcastic.isEmpty()) lastSarcasticReply = shorten(sarcastic, 180);\n            if (!funny.isEmpty()) lastFunnyReply = shorten(funny, 180);\n            if (!calm.isEmpty()) lastCalmReply = shorten(calm, 180);\n        }\n\n        long effectiveAt = shownAtMs > 0L ? shownAtMs : System.currentTimeMillis();\n        lastReplyAtMs = Math.max(lastReplyAtMs, effectiveAt);\n    }\n\n    synchronized void submitAudio(short[] samples, int sampleRate) {\n''',
)

replace_once(
    service,
    '''        if (!useful.isEmpty() && realtimeTranscriber != null) {\n            // Keep one STT context across Realtime and file fallback so reconnects\n            // continue from the actual latest conversation.\n            realtimeTranscriber.rememberAcceptedTranscript(useful);\n        }\n''',
    '''        if (fromRealtime && !useful.isEmpty() && aiClient != null) {\n            // Keep the file-reply lane on the same accepted conversation so a later\n            // fallback does not behave as if the recent Realtime turns never happened.\n            aiClient.rememberAcceptedTranscript(useful);\n        }\n        if (!useful.isEmpty() && realtimeTranscriber != null) {\n            // Keep one STT context across Realtime and file fallback so reconnects\n            // continue from the actual latest conversation.\n            realtimeTranscriber.rememberAcceptedTranscript(useful);\n        }\n''',
)

replace_once(
    service,
    '''        currentReplies = replies;\n        answerUpdatedAtMs = now;\n        if (answerText != null) answerText.setAlpha(1f);\n''',
    '''        currentReplies = replies;\n        answerUpdatedAtMs = now;\n        if (aiClient != null) aiClient.rememberShownReplies(replies, now);\n        if (answerText != null) answerText.setAlpha(1f);\n''',
)

replace_once(
    gradle,
    '''        versionCode = 43\n        versionName = "0.42.0-shared-stt-context"\n''',
    '''        versionCode = 44\n        versionName = "0.43.0-shared-reply-context"\n''',
)
