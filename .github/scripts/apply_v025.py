from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:120]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


def replace_exact_count(path, old, new, expected):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != expected:
        raise SystemExit(f"{path}: expected {expected} matches, got {count} for {old[:120]!r}")
    p.write_text(s.replace(old, new), encoding="utf-8")


rt = "app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java"
service = "app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java"
gradle = "app/build.gradle.kts"

replace_exact_count(
    rt,
    '''            if (!transcript.isEmpty()) {
                rememberTranscript(transcript);
                listener.onFinal(turn, transcript);
''',
    '''            if (!transcript.isEmpty()) {
                listener.onFinal(turn, transcript);
''',
    2,
)

replace_once(
    rt,
    '''    private void rememberTranscript(String transcript) {
        contextWindow.add(transcript, System.currentTimeMillis());
''',
    '''    void rememberAcceptedTranscript(String transcript) {
        contextWindow.add(transcript, System.currentTimeMillis());
''',
)

replace_once(
    service,
    '''        if (echoAdjusted) lastSelfEchoAtMs = now;
        if (!useful.isEmpty()) {
''',
    '''        if (echoAdjusted) lastSelfEchoAtMs = now;
        if (fromRealtime && !useful.isEmpty() && realtimeTranscriber != null) {
            realtimeTranscriber.rememberAcceptedTranscript(useful);
        }
        if (!useful.isEmpty()) {
''',
)

replace_once(
    gradle,
    '''        versionCode = 24
        versionName = "0.24.0-echo-tail"
''',
    '''        versionCode = 25
        versionName = "0.25.0-clean-stt-context"
''',
)
