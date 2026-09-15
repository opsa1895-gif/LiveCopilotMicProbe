from pathlib import Path

p = Path('app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java')
s = p.read_text()

old = '''        if (primaryOnly) {\n            semanticPrimaryAppliedAtMs = now;\n        }\n        // Terminal cleanup is owned by handleSemanticFinished(). This avoids leaving\n'''
new = '''        if (primaryOnly) {\n            semanticPrimaryAppliedAtMs = now;\n            String lower = aiStatus == null ? "" : aiStatus.toLowerCase(Locale.ROOT);\n            if (lower.contains("мисля") || lower.contains("проверявам")) {\n                aiStatus = "Слушам";\n                renderStatus();\n            }\n        }\n        // Terminal cleanup is owned by handleSemanticFinished(). This avoids leaving\n'''
if s.count(old) != 1:
    raise SystemExit(f'primary status anchor: expected 1, found {s.count(old)}')
s = s.replace(old, new, 1)

old = '''            pausedAtMs = System.currentTimeMillis();\n            if (semanticFallback != null) semanticFallback.invalidate();\n            activeSemanticRequestId = -1L;\n            activeSemanticEpoch = -1L;\n'''
new = '''            pausedAtMs = System.currentTimeMillis();\n            if (semanticFallback != null) semanticFallback.invalidate();\n            if (activeSemanticRequestId >= 0L) {\n                lastSemanticTerminal = "cancelled";\n                lastSemanticDecisionAttempts = 0;\n            }\n            activeSemanticRequestId = -1L;\n            activeSemanticEpoch = -1L;\n'''
if s.count(old) != 1:
    raise SystemExit(f'pause cancellation anchor: expected 1, found {s.count(old)}')
s = s.replace(old, new, 1)

p.write_text(s)
