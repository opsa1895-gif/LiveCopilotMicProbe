package com.livecopilot.micprobe;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

final class SemanticContextPolicy {
    private SemanticContextPolicy() {}

    static String buildRecent(Deque<String> turns, int maxChars) {
        if (turns == null || turns.isEmpty() || maxChars <= 0) return "";

        List<String> newestFirst = new ArrayList<>();
        int used = 0;
        Iterator<String> it = turns.descendingIterator();
        while (it.hasNext()) {
            String turn = clean(it.next());
            if (turn.isEmpty()) continue;

            String line = "- " + turn;
            int separator = newestFirst.isEmpty() ? 0 : 1;
            int available = maxChars - used - separator;
            if (available <= 2) break;

            if (line.length() > available) {
                if (!newestFirst.isEmpty()) break;
                line = shorten(line, available);
            }

            newestFirst.add(line);
            used += separator + line.length();
        }

        StringBuilder out = new StringBuilder();
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            if (out.length() > 0) out.append('\n');
            out.append(newestFirst.get(i));
        }
        return out.toString();
    }

    private static String shorten(String value, int maxChars) {
        if (maxChars <= 0) return "";
        if (value.length() <= maxChars) return value;
        if (maxChars == 1) return "…";
        return value.substring(0, maxChars - 1) + "…";
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
