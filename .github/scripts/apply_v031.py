from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:140]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


client = "app/src/main/java/com/livecopilot/micprobe/OpenAiCopilotClient.java"
gradle = "app/build.gradle.kts"

replace_once(
    client,
    '''    private static boolean isActionable(String text) {
        String v = normalize(text);
        if (v.isEmpty()) return false;
        if (text.contains("?")) return true;

        List<String> ws = words(v);
        if (!ws.isEmpty()) {
            String first = ws.get(0);
            if (first.equals("как") || first.equals("какво") || first.equals("защо") ||
                    first.equals("кой") || first.equals("коя") || first.equals("кое") || first.equals("кои") ||
                    first.equals("къде") || first.equals("кога") || first.equals("колко") ||
                    first.equals("откъде") || first.equals("дали")) return true;
            if (ws.size() >= 2 && ws.contains("ли")) return true;
        }

        return containsAny(v,
''',
    '''    static boolean isActionable(String text) {
        String v = normalize(text);
        if (v.isEmpty()) return false;
        if (text.contains("?")) return true;

        List<String> ws = words(v);
        if (!ws.isEmpty()) {
            int firstMeaningful = 0;
            int skipped = 0;
            while (firstMeaningful < ws.size() - 1
                    && skipped < 3
                    && isDiscoursePrefix(ws.get(firstMeaningful))) {
                firstMeaningful++;
                skipped++;
            }
            String first = ws.get(firstMeaningful);
            if (isQuestionStarter(first)) return true;
            if (ws.size() >= 2 && ws.contains("ли")) return true;
        }

        return containsAny(v,
''',
)

replace_once(
    client,
    '''    private static boolean isStrongTopicShift(String value) {
''',
    '''    private static boolean isQuestionStarter(String word) {
        return word.equals("как") || word.equals("какво") || word.equals("защо") ||
                word.equals("кой") || word.equals("коя") || word.equals("кое") || word.equals("кои") ||
                word.equals("къде") || word.equals("кога") || word.equals("колко") ||
                word.equals("откъде") || word.equals("дали");
    }

    private static boolean isDiscoursePrefix(String word) {
        return word.equals("а") || word.equals("и") || word.equals("ами") ||
                word.equals("добре") || word.equals("значи") || word.equals("чакай") ||
                word.equals("така") || word.equals("но");
    }

    private static boolean isStrongTopicShift(String value) {
''',
)

replace_once(
    gradle,
    '''        versionCode = 31
        versionName = "0.30.1-echo-fidelity-fix"
''',
    '''        versionCode = 32
        versionName = "0.31.0-actionable-prefixes"
''',
)
