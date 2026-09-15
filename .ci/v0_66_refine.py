from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"missing pattern in {path}: {old[:140]!r}")
    p.write_text(text.replace(old, new, 1))

client = 'app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java'
replace_once(client,
'''            if (attempt + 1 < maxAttempts) {
                if (!shouldProcessAudioResult(item)) {
                    throw new java.io.InterruptedIOException("stale transcription");
                }
                Thread.sleep(500L);
            }''',
'''            if (attempt + 1 < maxAttempts) {
                if (!FileSttRecoveryPolicy.shouldRetryFailure(last, attempt + 1, item.allowRetry)) break;
                if (!shouldProcessAudioResult(item)) {
                    throw new java.io.InterruptedIOException("stale transcription");
                }
                Thread.sleep(500L);
            }''')

service = 'app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java'
replace_once(service,
'''    private int lastFileDegradedStreak;
    private long lastFileRetryCooldownMs;
    private String lastSemanticTerminal = "";''',
'''    private int lastFileDegradedStreak;
    private long lastFileRetryCooldownUntilMs;
    private String lastSemanticTerminal = "";''')
replace_once(service,
'''        lastFileDegradedStreak = Math.max(0, degradedTurnStreak);
        lastFileRetryCooldownMs = Math.max(0L, retryCooldownRemainingMs);
        if (submittedChunks > 0 && failedChunks >= submittedChunks
                && lastFileTimeoutDrops + lastFileNetworkDrops > 0
                && lastFileRetryCooldownMs > 0L) {
            aiStatus = "Слушам • нестабилна връзка";
            renderStatus();
        }''',
'''        lastFileDegradedStreak = Math.max(0, degradedTurnStreak);
        long safeCooldownMs = Math.max(0L, retryCooldownRemainingMs);
        lastFileRetryCooldownUntilMs = safeCooldownMs > 0L
                ? System.currentTimeMillis() + safeCooldownMs : 0L;
        if (submittedChunks > 0 && failedChunks >= submittedChunks
                && lastFileTimeoutDrops + lastFileNetworkDrops > 0
                && fileRetryCooldownRemainingMs() > 0L) {
            aiStatus = "Слушам • нестабилна връзка";
            renderStatus();
        }''')
replace_once(service,
'''        String raw = status == null ? "" : status.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("мисля") || lower.contains("генерирам")) {''',
'''        String raw = status == null ? "" : status.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (!replyWork && lower.startsWith("слушам")
                && lastFileDegradedStreak >= 2
                && fileRetryCooldownRemainingMs() > 0L) {
            raw = "Слушам • нестабилна връзка";
            lower = raw.toLowerCase(Locale.ROOT);
        }
        if (lower.contains("мисля") || lower.contains("генерирам")) {''')
replace_once(service,
'''                + (lastFileDegradedStreak > 0 ? " • degraded×" + lastFileDegradedStreak : "")
                + (lastFileRetryCooldownMs > 0L
                ? " • retry-cd " + latencyLabel(lastFileRetryCooldownMs) : "");''',
'''                + (lastFileDegradedStreak > 0 ? " • degraded×" + lastFileDegradedStreak : "")
                + (fileRetryCooldownRemainingMs() > 0L
                ? " • retry-cd " + latencyLabel(fileRetryCooldownRemainingMs()) : "");''')
replace_once(service,
'''        lastFileDegradedStreak = 0;
        lastFileRetryCooldownMs = 0L;
        lastSemanticTerminal = "";''',
'''        lastFileDegradedStreak = 0;
        lastFileRetryCooldownUntilMs = 0L;
        lastSemanticTerminal = "";''')
anchor = '''    private static String latencyLabel(long ms) {
'''
helper = '''    private long fileRetryCooldownRemainingMs() {
        if (lastFileRetryCooldownUntilMs <= 0L) return 0L;
        return Math.max(0L, lastFileRetryCooldownUntilMs - System.currentTimeMillis());
    }

'''
replace_once(service, anchor, helper + anchor)

print('v0.66 refinement applied')
