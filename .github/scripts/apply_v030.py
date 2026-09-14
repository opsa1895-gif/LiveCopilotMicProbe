from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:140]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


filter_file = "app/src/main/java/com/livecopilot/micprobe/SelfEchoFilter.java"
test_file = "app/src/test/java/com/livecopilot/micprobe/SelfEchoFilterTest.java"
gradle = "app/build.gradle.kts"

replace_once(
    filter_file,
    '''            String tail = meaningfulTailAfterSuggestion(heard, suggested);
''',
    '''            String tail = meaningfulTailAfterSuggestion(original, heard, suggested);
''',
)

replace_once(
    filter_file,
    '''    private static String meaningfulTailAfterSuggestion(String heard, String suggested) {
        List<String> heardWords = words(heard);
        List<String> suggestedWords = words(suggested);
        if (heardWords.size() < 4 || suggestedWords.size() < 3) return null;

        int consumed = matchedSuggestionPrefixLength(heardWords, suggestedWords);
        if (consumed <= 0 || consumed >= heardWords.size()) return null;

        List<String> tailWords = heardWords.subList(consumed, heardWords.size());
        if (!isMeaningfulNovelTail(tailWords)) return null;
        return join(tailWords);
    }
''',
    '''    private static String meaningfulTailAfterSuggestion(String original, String heard, String suggested) {
        List<String> heardWords = words(heard);
        List<String> suggestedWords = words(suggested);
        if (heardWords.size() < 4 || suggestedWords.size() < 3) return null;

        int consumed = matchedSuggestionPrefixLength(heardWords, suggestedWords);
        if (consumed <= 0 || consumed >= heardWords.size()) return null;

        List<String> tailWords = heardWords.subList(consumed, heardWords.size());
        if (!isMeaningfulNovelTail(tailWords)) return null;
        return originalTailAfterWords(original, consumed);
    }

    private static String originalTailAfterWords(String original, int consumedWords) {
        if (original == null || original.isEmpty() || consumedWords < 0) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[\\p{L}\\p{N}]+").matcher(original);
        int seen = 0;
        while (matcher.find()) {
            if (seen == consumedWords) {
                return clean(original.substring(matcher.start()));
            }
            seen++;
        }
        return "";
    }
''',
)

replace_once(
    test_file,
    '''        assertEquals("а колко струва доставката", SelfEchoFilter.removeEchoPrefix(heard, suggestion));
''',
    '''        assertEquals("а колко струва доставката?", SelfEchoFilter.removeEchoPrefix(heard, suggestion));
''',
)

replace_once(
    test_file,
    '''    @Test
    public void shortFillerAfterSuggestionStillCountsAsEcho() {
''',
    '''    @Test
    public void novelTailPreservesOriginalCaseNamesAndPunctuation() {
        String suggestion = "Добре, нека продължим с темата.";
        String heard = "Добре нека продължим с темата. А Ива ще дойде ли утре?";

        assertEquals("А Ива ще дойде ли утре?", SelfEchoFilter.removeEchoPrefix(heard, suggestion));
    }

    @Test
    public void shortFillerAfterSuggestionStillCountsAsEcho() {
''',
)

replace_once(
    gradle,
    '''        versionCode = 29
        versionName = "0.29.0-host-style-parity"
''',
    '''        versionCode = 30
        versionName = "0.30.0-echo-fidelity"
''',
)
