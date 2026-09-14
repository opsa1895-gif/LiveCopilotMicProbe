from pathlib import Path

p = Path('app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java')
s = p.read_text()

def rep(old, new, count=1):
    global s
    actual = s.count(old)
    if actual < count:
        raise SystemExit(f'expected at least {count} occurrences, found {actual}: {old[:80]!r}')
    s = s.replace(old, new, count)

rep('''    // Absolute from chunk submission, so ordered waits cannot stack this delay\n    // once per chunk at the end of a long speech turn.\n    private static final long FILE_STT_COMMIT_DEADLINE_MS = 8_000L;\n''', '')

rep('''    private long fileTurnSerial = -1L;\n    private String fileTurnFocus = "";\n    private long lastReplyAtMs;\n''', '''    private long fileTurnSerial = -1L;\n    private String fileTurnFocus = "";\n    private long fileSttCommitDeadlineMs = AdaptiveFileSttDeadlinePolicy.initialDeadlineMs();\n    private boolean fileTurnHadSttTimeout;\n    private long lastReplyAtMs;\n''')

rep('''        fileTurnSerial = -1L;\n        fileTurnFocus = "";\n        recentTurns.clear();\n''', '''        fileTurnSerial = -1L;\n        fileTurnFocus = "";\n        fileSttCommitDeadlineMs = AdaptiveFileSttDeadlinePolicy.initialDeadlineMs();\n        fileTurnHadSttTimeout = false;\n        recentTurns.clear();\n''')

rep('''        fileTurnSerial = latestInputSerial;\n        fileTurnFocus = "";\n        audioExecutor.cancelPending();\n''', '''        fileTurnSerial = latestInputSerial;\n        fileTurnFocus = "";\n        fileTurnHadSttTimeout = false;\n        audioExecutor.cancelPending();\n''')

# Pause/invalidation preserves the learned deadline, but clears feedback from the
# interrupted turn so it cannot bias the next completed turn.
rep('''        latestInputSerial++;\n        latestReplySerial++;\n        fileTurnSerial = -1L;\n        fileTurnFocus = "";\n        audioExecutor.cancelPending();\n''', '''        latestInputSerial++;\n        latestReplySerial++;\n        fileTurnSerial = -1L;\n        fileTurnFocus = "";\n        fileTurnHadSttTimeout = false;\n        audioExecutor.cancelPending();\n''')

# A Realtime transcript also invalidates the file turn while preserving the learned
# network profile for the next file-fallback turn.
rep('''            latestInputSerial++;\n            latestReplySerial++;\n            fileTurnSerial = -1L;\n            fileTurnFocus = "";\n            audioExecutor.cancelPending();\n''', '''            latestInputSerial++;\n            latestReplySerial++;\n            fileTurnSerial = -1L;\n            fileTurnFocus = "";\n            fileTurnHadSttTimeout = false;\n            audioExecutor.cancelPending();\n''')

rep('''        AudioItem item = new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial);\n        audioExecutor.submit(\n''', '''        AudioItem item = new AudioItem(samples.clone(), sampleRate, sessionSerial, inputSerial);\n        long deadlineMs = fileSttCommitDeadlineMs;\n        audioExecutor.submit(\n''')

rep('''                        item.cancel();\n                        listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");\n''', '''                        item.cancel();\n                        noteFileSttTimeout(item);\n                        listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");\n''')

rep('''                },\n                FILE_STT_COMMIT_DEADLINE_MS);\n    }\n\n    synchronized void finishAudioTurn(long inputSerial) {\n''', '''                },\n                deadlineMs);\n    }\n\n    private synchronized void noteFileSttTimeout(AudioItem item) {\n        if (item == null || closed) return;\n        if (item.sessionSerial != sessionSerial || item.inputSerial != latestInputSerial) return;\n        fileTurnHadSttTimeout = true;\n    }\n\n    synchronized void finishAudioTurn(long inputSerial) {\n''')

rep('''            if (!FileTurnReplyPolicy.canFinalize(end.inputSerial, fileTurnSerial)\n                    || closed\n                    || end.sessionSerial != sessionSerial\n                    || end.inputSerial != latestInputSerial) return;\n\n            turnFocus = fileTurnFocus;\n''', '''            if (!FileTurnReplyPolicy.canFinalize(end.inputSerial, fileTurnSerial)\n                    || closed\n                    || end.sessionSerial != sessionSerial\n                    || end.inputSerial != latestInputSerial) return;\n\n            long drainLatencyMs = Math.max(0L, now - end.createdAtMs);\n            fileSttCommitDeadlineMs = AdaptiveFileSttDeadlinePolicy.nextDeadlineMs(\n                    fileSttCommitDeadlineMs, drainLatencyMs, fileTurnHadSttTimeout);\n            fileTurnHadSttTimeout = false;\n\n            turnFocus = fileTurnFocus;\n''')

p.write_text(s)
