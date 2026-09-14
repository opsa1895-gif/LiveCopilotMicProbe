from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)

client_path = Path("app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java")
client = client_path.read_text()
client = replace_once(
    client,
    '''    synchronized void rememberAcceptedTranscript(String transcript) {''',
    '''    synchronized void noteNewSpeech() {\n        if (closed) return;\n        // Invalidate in-flight file STT/reply work as soon as a newer utterance starts,\n        // rather than waiting for its final transcript.\n        latestInputSerial++;\n        latestReplySerial++;\n    }\n\n    synchronized void rememberAcceptedTranscript(String transcript) {''',
    "noteNewSpeech insertion",
)
client_path.write_text(client)

service_path = Path("app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java")
service = service_path.read_text()
service = replace_once(
    service,
    '''            public void onState(String state) {\n                getMainExecutor().execute(() -> {\n                    realtimeState = state == null ? "?" : state;\n                    renderDebug();\n                });\n            }''',
    '''            public void onState(String state) {\n                getMainExecutor().execute(() -> {\n                    realtimeState = state == null ? "?" : state;\n                    if (!"ready".equals(realtimeState)) realtimePartial = "";\n                    renderDebug();\n                });\n            }''',
    "realtime state cleanup",
)
service = replace_once(
    service,
    '''    public void onStreamTurnStart(int sampleRate) {\n        clearFallbackTurnAudio();\n        if (realtimeTranscriber != null) realtimeTranscriber.noteNewSpeech();\n        realtimeTurnActive = realtimeTranscriber != null && realtimeTranscriber.beginTurn(sampleRate);\n    }''',
    '''    public void onStreamTurnStart(int sampleRate) {\n        clearFallbackTurnAudio();\n        realtimePartial = "";\n\n        // New speech immediately makes older generated work stale. Do this before\n        // waiting for a final transcript so an old answer cannot pop over a new turn.\n        if (aiClient != null) aiClient.noteNewSpeech();\n        if (semanticFallback != null) semanticFallback.invalidate();\n        activeSemanticRequestId = -1L;\n        semanticPrimaryAppliedAtMs = 0L;\n        pendingSemanticFocus = "";\n\n        if (realtimeTranscriber != null) realtimeTranscriber.noteNewSpeech();\n        realtimeTurnActive = realtimeTranscriber != null && realtimeTranscriber.beginTurn(sampleRate);\n        renderDebug();\n    }''',
    "stream turn start invalidation",
)
service_path.write_text(service)

gradle_path = Path("app/build.gradle.kts")
gradle = gradle_path.read_text()
gradle = replace_once(gradle, 'versionCode = 47', 'versionCode = 48', 'versionCode')
gradle = replace_once(
    gradle,
    'versionName = "0.46.0-atomic-reply-freshness"',
    'versionName = "0.47.0-new-speech-invalidation"',
    'versionName',
)
gradle_path.write_text(gradle)
