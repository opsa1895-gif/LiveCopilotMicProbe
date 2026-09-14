from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:180]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


service = "app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java"
gradle = "app/build.gradle.kts"

replace_once(
    service,
    '''    private static final long SELF_ECHO_WINDOW_MS = 12_000L;
    private static final int SEMANTIC_CONTEXT_TURNS = 6;
''',
    '''    private static final long SELF_ECHO_WINDOW_MS = 12_000L;
    private static final long CROSS_SOURCE_DEDUP_WINDOW_MS = 6_000L;
    private static final int SEMANTIC_CONTEXT_TURNS = 6;
''',
)

replace_once(
    service,
    '''    private String lastTranscript = "";
    private String realtimePartial = "";
''',
    '''    private String lastTranscript = "";
    private String lastAcceptedSourceTranscript = "";
    private String realtimePartial = "";
''',
)

replace_once(
    service,
    '''    private long lastRealtimeTranscriptAtMs;
    private long answerUpdatedAtMs;
''',
    '''    private long lastRealtimeTranscriptAtMs;
    private long lastAcceptedSourceAtMs;
    private boolean lastAcceptedFromRealtime;
    private long answerUpdatedAtMs;
''',
)

replace_once(
    service,
    '''        if (echoAdjusted) lastSelfEchoAtMs = now;
        if (fromRealtime) lastRealtimeTranscriptAtMs = now;
        else lastFileTranscriptAtMs = now;
        if (fromRealtime && !useful.isEmpty() && realtimeTranscriber != null) {
''',
    '''        if (echoAdjusted) lastSelfEchoAtMs = now;

        String sourceTranscript = useful;
        if (!useful.isEmpty()
                && !lastAcceptedSourceTranscript.isEmpty()
                && fromRealtime != lastAcceptedFromRealtime
                && lastAcceptedSourceAtMs > 0L
                && now >= lastAcceptedSourceAtMs
                && now - lastAcceptedSourceAtMs <= CROSS_SOURCE_DEDUP_WINDOW_MS) {
            String novel = CrossSourceTranscriptPolicy.novelPart(lastAcceptedSourceTranscript, useful);
            if (novel.isEmpty()) {
                aiStatus = "Слушам";
                renderStatus();
                renderDebug();
                return;
            }
            useful = novel;
        }

        if (fromRealtime) lastRealtimeTranscriptAtMs = now;
        else lastFileTranscriptAtMs = now;
        lastAcceptedSourceTranscript = sourceTranscript;
        lastAcceptedSourceAtMs = now;
        lastAcceptedFromRealtime = fromRealtime;
        if (fromRealtime && !useful.isEmpty() && realtimeTranscriber != null) {
''',
)

replace_once(
    service,
    '''            lastTranscript = "";
            realtimePartial = "";
            lastRealtimeTurnSerial = 0L;
''',
    '''            lastTranscript = "";
            lastAcceptedSourceTranscript = "";
            lastAcceptedSourceAtMs = 0L;
            lastAcceptedFromRealtime = false;
            realtimePartial = "";
            lastRealtimeTurnSerial = 0L;
''',
)

replace_once(
    gradle,
    '''        versionCode = 33
        versionName = "0.32.0-transcript-quality"
''',
    '''        versionCode = 34
        versionName = "0.33.0-cross-source-dedup"
''',
)
