package com.livecopilot.micprobe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SelfEchoFilter {
    private SelfEchoFilter() {}

    static boolean matchesAny(String transcript, String... candidates) {
        String heard = normalize(transcript);
        if (heard.isEmpty() || candidates == null) return false;
        for (String candidate : candidates) {
            if (matchesNormalized(heard, normalize(candidate))) return true;
        }
        return false;
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

    private static List<String> words(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isEmpty()) return out;
        for (String part : value.split("\\s+")) {
            if (!part.isEmpty()) out.add(part);
        }
        return out;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
