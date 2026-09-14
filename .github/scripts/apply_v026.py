from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:120]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


semantic = "app/src/main/java/com/livecopilot/micprobe/SemanticReplyFallback.java"
service = "app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java"
gradle = "app/build.gradle.kts"

replace_once(
    semantic,
    '''    private final ExecutorService decisionExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService variantExecutor = Executors.newSingleThreadExecutor();
''',
    '''    private final LatestWinsExecutor decisionExecutor = new LatestWinsExecutor(2);
    private final LatestWinsExecutor variantExecutor = new LatestWinsExecutor(1);
''',
)

replace_once(
    service,
    '''    private static final long SEMANTIC_MIN_GAP_MS = 4_000L;
''',
    '''    private static final long SEMANTIC_MIN_GAP_MS = 2_500L;
''',
)

replace_once(
    gradle,
    '''        versionCode = 25
        versionName = "0.25.0-clean-stt-context"
''',
    '''        versionCode = 26
        versionName = "0.26.0-fresh-semantic"
''',
)
