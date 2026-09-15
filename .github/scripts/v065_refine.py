from pathlib import Path

p = Path('app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java')
s = p.read_text()

replacements = [
    ('    private void requestSemanticFallback(String focus, long focusAtMs, long expectedSemanticEpoch,\n',
     '    private synchronized void requestSemanticFallback(String focus, long focusAtMs, long expectedSemanticEpoch,\n',
     'requestSemanticFallback synchronization'),
    ('    private void applySemanticReply(long requestId, OpenAiCopilotClient.Replies replies) {\n',
     '    private synchronized void applySemanticReply(long requestId, OpenAiCopilotClient.Replies replies) {\n',
     'applySemanticReply synchronization'),
    ('    private void toggleRunning() {\n',
     '    private synchronized void toggleRunning() {\n',
     'pause/resume synchronization'),
]

for old, new, label in replacements:
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, found {count}')
    s = s.replace(old, new, 1)

# Preserve useful diagnostics when new speech or pause cancels an in-flight semantic request.
old = '''        if (semanticFallback != null) semanticFallback.invalidate();\n        activeSemanticRequestId = -1L;\n        activeSemanticEpoch = -1L;\n'''
new = '''        if (semanticFallback != null) semanticFallback.invalidate();\n        if (activeSemanticRequestId >= 0L) {\n            lastSemanticTerminal = "cancelled";\n            lastSemanticDecisionAttempts = 0;\n        }\n        activeSemanticRequestId = -1L;\n        activeSemanticEpoch = -1L;\n'''
count = s.count(old)
if count != 2:
    raise SystemExit(f'semantic cancellation sites: expected 2 occurrences, found {count}')
s = s.replace(old, new)

p.write_text(s)
