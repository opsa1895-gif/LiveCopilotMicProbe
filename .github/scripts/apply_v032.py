from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:160]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


client = "app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java"
service = "app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java"
gradle = "app/build.gradle.kts"

replace_once(
    client,
    """            if (lowQuality(raw)) {
""",
    """            if (TranscriptQualityPolicy.isLowQuality(raw)) {
""",
)

replace_once(
    client,
    '''    private static boolean lowQuality(String value) {
        String s = clean(value);
        if (s.length() < 2) return true;
        int alnum = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetterOrDigit(s.charAt(i))) alnum++;
        if (alnum < 2) return true;
        String v = s.toLowerCase(Locale.ROOT);
        return v.equals("music") || v.equals("музика") || v.equals("[music]") || v.equals("...");
    }

''',
    '',
)

replace_once(
    service,
    '''    @Override
    public void onTranscript(String transcript) {
        lastFileTranscriptAtMs = System.currentTimeMillis();
        acceptTranscript(transcript, false);
    }

    private void handleRealtimeFinal(long turnSerial, String transcript) {
        if (turnSerial <= lastRealtimeTurnSerial) return;
        lastRealtimeTurnSerial = turnSerial;
        lastRealtimeTranscriptAtMs = System.currentTimeMillis();
        realtimePartial = "";
        acceptTranscript(transcript, true);
    }
''',
    '''    @Override
    public void onTranscript(String transcript) {
        acceptTranscript(transcript, false);
    }

    private void handleRealtimeFinal(long turnSerial, String transcript) {
        if (turnSerial <= lastRealtimeTurnSerial) return;
        lastRealtimeTurnSerial = turnSerial;
        realtimePartial = "";
        acceptTranscript(transcript, true);
    }
''',
)

replace_once(
    service,
    '''    private void acceptTranscript(String transcript, boolean fromRealtime) {
        String clean = transcript == null ? "" : transcript.replace('\\n', ' ').trim();
        long now = System.currentTimeMillis();
        if (!clean.isEmpty()
''',
    '''    private void acceptTranscript(String transcript, boolean fromRealtime) {
        String clean = transcript == null ? "" : transcript.replace('\\n', ' ').trim();
        long now = System.currentTimeMillis();
        if (TranscriptQualityPolicy.isLowQuality(clean)) {
            aiStatus = "Слушам";
            renderStatus();
            renderDebug();
            return;
        }
        if (!clean.isEmpty()
''',
)

replace_once(
    service,
    '''        if (echoAdjusted) lastSelfEchoAtMs = now;
        if (fromRealtime && !useful.isEmpty() && realtimeTranscriber != null) {
''',
    '''        if (echoAdjusted) lastSelfEchoAtMs = now;
        if (fromRealtime) lastRealtimeTranscriptAtMs = now;
        else lastFileTranscriptAtMs = now;
        if (fromRealtime && !useful.isEmpty() && realtimeTranscriber != null) {
''',
)

replace_once(
    gradle,
    '''        versionCode = 32
        versionName = "0.31.0-actionable-prefixes"
''',
    '''        versionCode = 33
        versionName = "0.32.0-transcript-quality"
''',
)
