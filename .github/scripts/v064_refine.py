from pathlib import Path

p = Path('app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java')
s = p.read_text()
old = '''        if (TranscriptQualityPolicy.isLowQuality(raw)) {\n            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");\n            return;\n        }\n'''
new = '''        if (TranscriptQualityPolicy.isLowQuality(raw)) {\n            // A current speech chunk that produced unusable STT still lowers turn\n            // coverage. This prevents a misleading 100% confidence score when the\n            // network succeeded but recognition did not yield usable text.\n            noteFileSttFailure(item, false);\n            listener.onStatus(item.sessionSerial, item.inputSerial, false, "Слушам");\n            return;\n        }\n'''
if s.count(old) != 1:
    raise SystemExit(f'low-quality STT block: expected 1 occurrence, found {s.count(old)}')
s = s.replace(old, new, 1)
p.write_text(s)
