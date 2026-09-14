package com.livecopilot.micprobe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SelfEchoFilter {
    private SelfEchoFilter() {}

    static boolean matchesAny(String transcript, String... candidates) {
        String filtered = removeEchoPrefix(transcript, candidates);
        return !clean(transcript).isEmpty() && filtered.isEmpty();
    }

    /**
     * Returns the useful non-echo content. A pure recent suggestion repeat becomes an empty string.
     * If the host repeats the suggestion and immediately adds new speech, only that novel tail is kept.
     * A non-echo utterance is returned cleaned but otherwise intact.
     */
    static String removeEchoPrefix(String transcript, String... candidates) {
        String original = clean(transcript);
        String heard = normalize(original);
        if (heard.isEmpty()) return "";
        if (candidates == null) return original;

        for (String candidate : candidates) {
            String suggested = normalize(candidate);
            if (suggested.isEmpty()) continue;

            String tail = meaningfulTailAfterSuggestion(original, heard, suggested);
            if (tail != null) return tail;

            if (matchesNormalized(heard, suggested)) return "";
        }
        return original;
    }

    private static String meaningfulTailAfterSuggestion(String original, String heard, String suggested) {
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

    private static int matchedSuggestionPrefixLength(List<String> heard, List<String> suggested) {
        int candidateSize = suggested.size();
        if (heard.size() < Math.min(3, candidateSize)) return 0;

        int direct = 0;
        int limit = Math.min(heard.size(), candidateSize);
        while (direct < limit && heard.get(direct).equals(suggested.get(direct))) direct++;
        if (direct == candidateSize) return candidateSize;

        // Allow one small STT mismatch while the host is clearly repeating the beginning of the suggestion.
        int compare = Math.min(candidateSize, heard.size());
        int samePosition = 0;
        for (int i = 0; i < compare; i++) {
            if (heard.get(i).equals(suggested.get(i))) samePosition++;
        }
        int required = Math.max(3, (int) Math.ceil(candidateSize * 0.80));
        if (samePosition >= required && heard.size() > candidateSize) return candidateSize;
        return 0;
    }

    private static boolean isMeaningfulNovelTail(List<String> tail) {
        if (tail == null || tail.isEmpty()) return false;
        if (tail.size() >= 3) return true;

        String first = tail.get(0);
        if (isQuestionStarter(first)) return true;
        if (first.equals("а") && tail.size() >= 2 && isQuestionStarter(tail.get(1))) return true;
        return false;
    }

    private static boolean isQuestionStarter(String word) {
        return word.equals("как") || word.equals("какво") || word.equals("защо")
                || word.equals("кой") || word.equals("коя") || word.equals("кое") || word.equals("кои")
                || word.equals("къде") || word.equals("кога") || word.equals("колко")
                || word.equals("откъде") || word.equals("дали");
    }

    private static boolean matchesNormalized(String heard, String suggested) {
        if (heard.isEmpty() || suggested.isEmpty()) return false;
        if (heard.equals(suggested)) return true;

        List<String> a = words(heard);
        List<String> b = words(suggested);
        if (a.size() < 3 || b.size() < 3) return false;

        String shorter = heard.length() <= suggested.length() ? heard : suggested;
        String longer = heard.length() > suggested.length() ? heard : suggested;
        if (longer.contains(shorter) && words(shorter).size() >= 4) return true;

        Set<String> as = new HashSet<>(a);
        Set<String> bs = new HashSet<>(b);
        int common = 0;
        for (String word : as) if (bs.contains(word)) common++;
        int smaller = Math.min(as.size(), bs.size());
        int larger = Math.max(as.size(), bs.size());
        if (smaller == 0) return false;

        double coverageOfShorter = common / (double) smaller;
        double coverageOfLonger = common / (double) larger;
        return common >= 3 && coverageOfShorter >= 0.78 && coverageOfLonger >= 0.55;
    }

    private static String join(List<String> value) {
        StringBuilder out = new StringBuilder();
        for (String word : value) {
            if (out.length() > 0) out.append(' ');
            out.append(word);
        }
        return out.toString();
    }

    private static List<String> words(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isEmpty()) return out;
        for (String part : value.split("\\s+")) {
            if (!part.isEmpty()) out.add(part);
        }
        return out;
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
