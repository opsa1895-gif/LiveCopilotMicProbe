from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:160]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


service = "app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java"
gradle = "app/build.gradle.kts"

replace_once(
    service,
    '''    private static final long SEMANTIC_MIN_GAP_MS = 2_500L;\n''',
    '',
)

replace_once(
    service,
    '''        long wait = Math.max(0L, SEMANTIC_MIN_GAP_MS - (now - lastSemanticRequestAtMs));\n        String focusSnapshot = pendingSemanticFocus;\n        long focusTimeSnapshot = pendingSemanticAtMs;\n''',
    '''        String focusSnapshot = pendingSemanticFocus;\n        long focusTimeSnapshot = pendingSemanticAtMs;\n        long minGapMs = SemanticSchedulingPolicy.minGapMs(focusSnapshot);\n        long wait = Math.max(0L, minGapMs - (now - lastSemanticRequestAtMs));\n''',
)

replace_once(
    service,
    '''        if (now - lastSemanticRequestAtMs < SEMANTIC_MIN_GAP_MS) return;\n''',
    '''        if (now - lastSemanticRequestAtMs < SemanticSchedulingPolicy.minGapMs(focus)) return;\n''',
)

replace_once(
    gradle,
    '''        versionCode = 36\n        versionName = "0.35.0-adaptive-endpointing"\n''',
    '''        versionCode = 37\n        versionName = "0.36.0-fast-actionable"\n''',
)
