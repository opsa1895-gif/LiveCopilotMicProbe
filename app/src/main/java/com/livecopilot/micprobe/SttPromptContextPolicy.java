package com.livecopilot.micprobe;

import java.util.ArrayList;
import java.util.List;

final class SttPromptContextPolicy {
    private SttPromptContextPolicy() {}

    static String buildRecent(List<String> turns, int maxChars) {
        if (turns == null || turns.isEmpty() || maxChars <= 0) return "";

        List<String> newestFirst = new ArrayList<>();
        int used = 0;
        for (int i = turns.size() - 1; i >= 0; i--) {
            String turn = clean(turns.get(i));
            if (turn.isEmpty()) continue;

            int separator = newestFirst.isEmpty() ? 0 : 3; // " | "
            int available = maxChars - used - separator;
            if (available <= 0) break;

            if (turn.length() > available) {
                if (!newestFirst.isEmpty()) break;
                turn = shorten(turn, available);
            }

            newestFirst.add(turn);
            used += separator + turn.length();
        }

        StringBuilder out = new StringBuilder();
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            if (out.length() > 0) out.append(" | ");
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
