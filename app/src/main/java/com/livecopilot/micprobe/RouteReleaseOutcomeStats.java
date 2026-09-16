package com.livecopilot.micprobe;

final class RouteReleaseOutcomeStats {
    private static final int REASON_RT_SLOWDOWN = 0;
    private static final int REASON_RT_SPEEDUP = 1;
    private static final int REASON_FILE_SPEEDUP = 2;
    private static final int REASON_FILE_SLOWDOWN = 3;
    private static final int REASON_COUNT = 4;

    private static final int OUTCOME_STABLE = 0;
    private static final int OUTCOME_REVERSAL = 1;
    private static final int OUTCOME_EXPIRED = 2;
    private static final int OUTCOME_COUNT = 3;

    private final int[][] counts = new int[REASON_COUNT][OUTCOME_COUNT];

    void record(String releaseReason, String outcome) {
        int reason = reasonIndex(releaseReason);
        int result = outcomeIndex(outcome);
        if (reason < 0 || result < 0) return;
        if (counts[reason][result] < Integer.MAX_VALUE) counts[reason][result]++;
    }

    int count(String releaseReason, String outcome) {
        int reason = reasonIndex(releaseReason);
        int result = outcomeIndex(outcome);
        if (reason < 0 || result < 0) return 0;
        return counts[reason][result];
    }

    void clear() {
        for (int reason = 0; reason < REASON_COUNT; reason++) {
            for (int outcome = 0; outcome < OUTCOME_COUNT; outcome++) {
                counts[reason][outcome] = 0;
            }
        }
    }

    boolean isEmpty() {
        for (int reason = 0; reason < REASON_COUNT; reason++) {
            for (int outcome = 0; outcome < OUTCOME_COUNT; outcome++) {
                if (counts[reason][outcome] > 0) return false;
            }
        }
        return true;
    }

    String diagnostics() {
        if (isEmpty()) return "";
        StringBuilder out = new StringBuilder("rel s/r/x");
        for (int reason = 0; reason < REASON_COUNT; reason++) {
            if (reasonTotal(reason) <= 0) continue;
            out.append(' ').append(reasonLabel(reason)).append(' ')
                    .append(counts[reason][OUTCOME_STABLE]).append('/')
                    .append(counts[reason][OUTCOME_REVERSAL]).append('/')
                    .append(counts[reason][OUTCOME_EXPIRED]);
        }
        return out.toString();
    }

    private int reasonTotal(int reason) {
        return counts[reason][OUTCOME_STABLE]
                + counts[reason][OUTCOME_REVERSAL]
                + counts[reason][OUTCOME_EXPIRED];
    }

    private static int reasonIndex(String reason) {
        if ("rt-slowdown".equals(reason)) return REASON_RT_SLOWDOWN;
        if ("rt-speedup".equals(reason)) return REASON_RT_SPEEDUP;
        if ("file-speedup".equals(reason)) return REASON_FILE_SPEEDUP;
        if ("file-slowdown".equals(reason)) return REASON_FILE_SLOWDOWN;
        return -1;
    }

    private static String reasonLabel(int reason) {
        if (reason == REASON_RT_SLOWDOWN) return "rtslow";
        if (reason == REASON_RT_SPEEDUP) return "rtfast";
        if (reason == REASON_FILE_SPEEDUP) return "ffast";
        if (reason == REASON_FILE_SLOWDOWN) return "fslow";
        return "?";
    }

    private static int outcomeIndex(String outcome) {
        if ("stable".equals(outcome)) return OUTCOME_STABLE;
        if ("reversal".equals(outcome)) return OUTCOME_REVERSAL;
        if ("expired".equals(outcome)) return OUTCOME_EXPIRED;
        return -1;
    }
}
