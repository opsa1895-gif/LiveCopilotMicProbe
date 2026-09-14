from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:180]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


client = "app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java"
gradle = "app/build.gradle.kts"

replace_once(
    client,
    '''        List<String> recent = contextWindow.snapshot(System.currentTimeMillis());\n        if (!recent.isEmpty()) {\n            out.append(" Предишен контекст: ");\n            for (int i = 0; i < recent.size(); i++) {\n                if (i > 0) out.append(" | ");\n                out.append(recent.get(i));\n                if (out.length() >= 640) break;\n            }\n            out.append('.');\n        }\n''',
    '''        List<String> recent = contextWindow.snapshot(System.currentTimeMillis());\n        if (!recent.isEmpty()) {\n            String prefix = " Предишен контекст: ";\n            int remaining = Math.max(0, 640 - out.length() - prefix.length() - 1);\n            String recentText = SttPromptContextPolicy.buildRecent(recent, remaining);\n            if (!recentText.isEmpty()) {\n                out.append(prefix).append(recentText).append('.');\n            }\n        }\n''',
)

replace_once(
    gradle,
    '''        versionCode = 44\n        versionName = "0.43.0-shared-reply-context"\n''',
    '''        versionCode = 45\n        versionName = "0.44.0-newest-stt-context"\n''',
)
