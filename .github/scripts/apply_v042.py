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
    '''        if (fromRealtime && !useful.isEmpty() && realtimeTranscriber != null) {\n            realtimeTranscriber.rememberAcceptedTranscript(useful);\n        }\n''',
    '''        if (!useful.isEmpty() && realtimeTranscriber != null) {\n            // Keep one STT context across Realtime and file fallback so reconnects\n            // continue from the actual latest conversation.\n            realtimeTranscriber.rememberAcceptedTranscript(useful);\n        }\n''',
)

replace_once(
    gradle,
    '''        versionCode = 42\n        versionName = "0.41.0-latest-recovery"\n''',
    '''        versionCode = 43\n        versionName = "0.42.0-shared-stt-context"\n''',
)
