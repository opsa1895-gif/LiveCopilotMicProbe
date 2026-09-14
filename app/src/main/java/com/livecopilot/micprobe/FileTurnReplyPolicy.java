package com.livecopilot.micprobe;

final class FileTurnReplyPolicy {
    static final int MAX_FOCUS_CHARS = 700;
    private static final int HEAD_CHARS = 260;

    private FileTurnReplyPolicy() {}

    static boolean canFinalize(long endSerial, long activeTurnSerial) {
        return endSerial > 0L && endSerial == activeTurnSerial;
    }

    static boolean shouldUseSemanticFallback(boolean mainReplyQueued, String turnFocus) {
        return !mainReplyQueued && !clean(turnFocus).isEmpty();
    }

    static String appendFocus(String current, String novel, boolean topicShift) {
        String before = clean(current);
        String addition = clean(novel);
        if (addition.isEmpty()) return topicShift ? "" : before;

        String combined = topicShift || before.isEmpty()
                ? addition
                : before + " " + addition;
        if (combined.length() <= MAX_FOCUS_CHARS) return combined;

        int tailChars = MAX_FOCUS_CHARS - HEAD_CHARS - 3;
        String head = combined.substring(0, HEAD_CHARS).trim();
        String tail = combined.substring(combined.length() - tailChars).trim();
        return head + " … " + tail;
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ").trim();
    }
}
