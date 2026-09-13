package com.livecopilot.micprobe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

final class SttContextWindow {
    private final int maxTurns;
    private final long idleResetMs;
    private final Deque<String> turns = new ArrayDeque<>();
    private long lastTurnAtMs;

    SttContextWindow(int maxTurns, long idleResetMs) {
        this.maxTurns = Math.max(1, maxTurns);
        this.idleResetMs = Math.max(1L, idleResetMs);
    }

    synchronized void add(String raw, long nowMs) {
        String clean = clean(raw);
        if (clean.isEmpty()) return;

        expireIfIdle(nowMs);
        if (isTopicShift(clean)) turns.clear();

        if (clean.length() > 180) clean = clean.substring(0, 180).trim();
        String previous = turns.peekLast();
        if (previous == null || !previous.equalsIgnoreCase(clean)) {
            turns.addLast(clean);
            while (turns.size() > maxTurns) turns.removeFirst();
        }
        lastTurnAtMs = Math.max(0L, nowMs);
    }

    synchronized List<String> snapshot(long nowMs) {
        expireIfIdle(nowMs);
        return new ArrayList<>(turns);
    }

    synchronized void clear() {
        turns.clear();
        lastTurnAtMs = 0L;
    }

    private void expireIfIdle(long nowMs) {
        if (lastTurnAtMs > 0L && nowMs >= lastTurnAtMs && nowMs - lastTurnAtMs >= idleResetMs) {
            turns.clear();
            lastTurnAtMs = 0L;
        }
    }

    static boolean isTopicShift(String raw) {
        String value = clean(raw).toLowerCase(Locale.ROOT);
        return value.equals("между другото") || value.startsWith("между другото ")
                || value.equals("друга тема") || value.startsWith("друга тема ")
                || value.equals("нов въпрос") || value.startsWith("нов въпрос ")
                || value.equals("друго нещо") || value.startsWith("друго нещо ")
                || value.equals("сменям темата") || value.startsWith("сменям темата ");
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
    }
}
