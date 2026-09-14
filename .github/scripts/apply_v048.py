from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)

service_path = Path("app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java")
service = service_path.read_text()

service = replace_once(
    service,
    '''    private volatile boolean realtimeTurnActive;\n    private short[] fallbackTurnAudio;''',
    '''    private volatile boolean realtimeTurnActive;\n    private volatile boolean realtimeBackupStreaming;\n    private short[] fallbackTurnAudio;''',
    "backup flag field",
)

service = replace_once(
    service,
    '''        if (realtimeTranscriber != null) realtimeTranscriber.noteNewSpeech();\n        realtimeTurnActive = realtimeTranscriber != null && realtimeTranscriber.beginTurn(sampleRate);\n        renderDebug();''',
    '''        if (realtimeTranscriber != null) realtimeTranscriber.noteNewSpeech();\n        realtimeTurnActive = realtimeTranscriber != null && realtimeTranscriber.beginTurn(sampleRate);\n        // Only turns that actually entered Realtime need a contiguous emergency\n        // backup. Pure file-fallback turns keep using the chunk/overlap path.\n        realtimeBackupStreaming = realtimeTurnActive;\n        renderDebug();''',
    "turn start backup mode",
)

service = replace_once(
    service,
    '''    public void onPcmStream(short[] samples, int sampleRate) {\n        if (!realtimeTurnActive || realtimeTranscriber == null) return;\n        if (!realtimeTranscriber.append(samples, sampleRate)) {\n            realtimeTurnActive = false;\n        }\n    }''',
    '''    public void onPcmStream(short[] samples, int sampleRate) {\n        // Capture the exact stream once, without the engine's forced-split overlap.\n        // If Realtime dies mid-turn we keep buffering the rest for one clean file STT.\n        if (realtimeBackupStreaming) appendFallbackTurnAudio(samples, sampleRate);\n\n        if (!realtimeTurnActive || realtimeTranscriber == null) return;\n        if (!realtimeTranscriber.append(samples, sampleRate)) {\n            realtimeTurnActive = false;\n        }\n    }''',
    "pcm stream backup",
)

service = replace_once(
    service,
    '''    public void onStreamTurnEnd() {\n        if (!realtimeTurnActive || realtimeTranscriber == null) {\n            realtimeTurnActive = false;\n            return;\n        }\n        boolean committed = realtimeTranscriber.commitTurn();\n        realtimeTurnActive = false;\n        if (!committed) submitBufferedFallback();\n        else clearFallbackTurnAudio();\n    }''',
    '''    public void onStreamTurnEnd() {\n        boolean hadRealtimeBackup = realtimeBackupStreaming;\n        boolean committed = hadRealtimeBackup\n                && realtimeTurnActive\n                && realtimeTranscriber != null\n                && realtimeTranscriber.commitTurn();\n\n        realtimeTurnActive = false;\n        realtimeBackupStreaming = false;\n        if (!hadRealtimeBackup) return;\n\n        if (!committed) submitBufferedFallback();\n        else clearFallbackTurnAudio();\n    }''',
    "turn end routing",
)

service = replace_once(
    service,
    '''        if (realtimeTurnActive) {\n            appendFallbackTurnAudio(samples, sampleRate);\n            return;\n        }''',
    '''        // A Realtime-started turn is already backed up continuously by\n        // onPcmStream(). Ignoring overlap chunks here prevents repeated audio.\n        if (realtimeBackupStreaming) return;''',
    "chunk overlap suppression",
)

service = replace_once(
    service,
    '''            pendingSemanticFocus = "";\n            realtimeTurnActive = false;\n            clearFallbackTurnAudio();''',
    '''            pendingSemanticFocus = "";\n            realtimeTurnActive = false;\n            realtimeBackupStreaming = false;\n            clearFallbackTurnAudio();''',
    "pause reset",
)

service_path.write_text(service)

gradle_path = Path("app/build.gradle.kts")
gradle = gradle_path.read_text()
gradle = replace_once(gradle, 'versionCode = 48', 'versionCode = 49', 'versionCode')
gradle = replace_once(
    gradle,
    'versionName = "0.47.0-new-speech-invalidation"',
    'versionName = "0.48.0-contiguous-fallback-audio"',
    'versionName',
)
gradle_path.write_text(gradle)
