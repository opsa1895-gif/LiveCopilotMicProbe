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
    '''    private static final int SEMANTIC_CONTEXT_TURNS = 6;
''',
    '''    private static final int SEMANTIC_CONTEXT_TURNS = 10;
    private static final int SEMANTIC_CONTEXT_MAX_CHARS = 900;
''',
)

replace_once(
    service,
    '''    private String buildSemanticContext() {
        StringBuilder out = new StringBuilder();
        for (String turn : semanticTurns) {
            if (out.length() > 0) out.append('\\n');
            out.append("- ").append(turn);
        }
        return out.toString();
    }
''',
    '''    private String buildSemanticContext() {
        return SemanticContextPolicy.buildRecent(semanticTurns, SEMANTIC_CONTEXT_MAX_CHARS);
    }
''',
)

replace_once(
    gradle,
    '''        versionCode = 34
        versionName = "0.33.0-cross-source-dedup"
''',
    '''        versionCode = 35
        versionName = "0.34.0-recent-context"
''',
)
