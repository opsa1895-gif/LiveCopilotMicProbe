from pathlib import Path

p = Path('app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java')
s = p.read_text()

replacements = [
    ('    private synchronized void toggleRunning() {\n',
     '    private void toggleRunning() {\n',
     'toggle lock removal'),
    ('    public synchronized void onStreamTurnStart(int sampleRate) {\n        clearFallbackTurnAudio();\n',
     '    public synchronized void onStreamTurnStart(int sampleRate) {\n        if (engine == null || !engine.isRunning()) return;\n        clearFallbackTurnAudio();\n',
     'turn start running guard'),
    ('    public void onPcmStream(short[] samples, int sampleRate) {\n        // Capture the exact stream once, without the engine\'s forced-split overlap.\n',
     '    public void onPcmStream(short[] samples, int sampleRate) {\n        if (engine == null || !engine.isRunning()) return;\n        // Capture the exact stream once, without the engine\'s forced-split overlap.\n',
     'pcm stream running guard'),
    ('    public void onStreamTurnEnd() {\n        boolean hadRealtimeBackup = realtimeBackupStreaming;\n',
     '    public void onStreamTurnEnd() {\n        if (engine == null || !engine.isRunning()) return;\n        boolean hadRealtimeBackup = realtimeBackupStreaming;\n',
     'turn end running guard'),
]

for old, new, label in replacements:
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, found {count}')
    s = s.replace(old, new, 1)

p.write_text(s)
