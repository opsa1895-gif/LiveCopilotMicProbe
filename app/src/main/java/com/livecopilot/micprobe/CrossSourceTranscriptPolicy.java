package com.livecopilot.micprobe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CrossSourceTranscriptPolicy {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private CrossSourceTranscriptPolicy() {}

    static String novelPart(String previous, String current) {
        String prev = clean(previous);
        String cur = clean(current);
        if (cur.isEmpty()) return "";
        if (prev.isEmpty()) return cur;

        List<String> a = words(prev);
        List<String> b = words(cur);
        if (b.isEmpty()) return "";
        if (a.isEmpty()) return cur;

        if (a.equals(b)) return "";
        if (b.size() >= 3 && containsSequence(a, b)) return "";
        if (nearDuplicate(a, b)) return "";

        int prefix = matchingPrefix(a, b);
        if (prefix == a.size() && prefix < b.size()) {
            return originalTailAfterWords(cur, prefix);
        }

        int overlap = suffixPrefixOverlap(a, b);
        if (overlap >= 2 && overlap < b.size()) {
            return originalTailAfterWords(cur, overlap);
        }

        return cur;
    }

    private static int matchingPrefix(List<String> a, List<String> b) {
        int limit = Math.min(a.size(), b.size());
        int n = 0;
        while (n < limit && a.get(n).equals(b.get(n))) n++;
        return n;
    }

    private static int suffixPrefixOverlap(List<String> a, List<String> b) {
        int max = Math.min(Math.min(a.size(), b.size()), 14);
        int best = 0;
        for (int n = 2; n <= max; n++) {
            boolean match = true;
            for (int i = 0; i < n; i++) {
                if (!a.get(a.size() - n + i).equals(b.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) best = n;
        }
        return best;
    }

    private static boolean containsSequence(List<String> haystack, List<String> needle) {
        if (needle.size() > haystack.size()) return false;
        for (int start = 0; start <= haystack.size() - needle.size(); start++) {
            boolean match = true;
            for (int i = 0; i < needle.size(); i++) {
                if (!haystack.get(start + i).equals(needle.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }

    private static boolean nearDuplicate(List<String> a, List<String> b) {
        if (a.size() < 3 || b.size() < 3) return false;
        Set<String> as = new HashSet<>(a);
        Set<String> bs = new HashSet<>(b);
        int common = 0;
        for (String word : as) if (bs.contains(word)) common++;
        int smaller = Math.min(as.size(), bs.size());
        int larger = Math.max(as.size(), bs.size());
        if (smaller == 0) return false;
        return common >= 3
                && common / (double) smaller >= 0.86
                && common / (double) larger >= 0.72;
    }

    private static String originalTailAfterWords(String original, int consumedWords) {
        Matcher matcher = WORD.matcher(original);
        int seen = 0;
        while (matcher.find()) {
            if (seen == consumedWords) return clean(original.substring(matcher.start()));
            seen++;
        }
        return "";
    }

    private static List<String> words(String value) {
        String normalized = clean(value).toLowerCase(Locale.ROOT);
        Matcher matcher = WORD.matcher(normalized);
        List<String> out = new ArrayList<>();
        while (matcher.find()) out.add(matcher.group());
        return out;
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
