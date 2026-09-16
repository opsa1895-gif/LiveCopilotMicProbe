package com.livecopilot.micprobe;

final class RouteReleaseOutcomeStats {
    static final int MIN_REVERSAL_RATE_SAMPLES = 3;
    static final int REVERSAL_RATE_WINDOW_SAMPLES = 8;
    static final int STABLE_REVERSAL_RATE_MAX_PERCENT = 25;
    static final int HIGH_REVERSAL_RISK_MIN_PERCENT = 50;

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
    private final int[][] recentDirectionalOutcomes =
            new int[REASON_COUNT][REVERSAL_RATE_WINDOW_SAMPLES];
    private final int[] recentDirectionalNext = new int[REASON_COUNT];
    private final int[] recentDirectionalSize = new int[REASON_COUNT];
    private final int[] recentDirectionalReversals = new int[REASON_COUNT];

    void record(String releaseReason, String outcome) {
        int reason = reasonIndex(releaseReason);
        int result = outcomeIndex(outcome);
        if (reason < 0 || result < 0) return;
        if (counts[reason][result] < Integer.MAX_VALUE) counts[reason][result]++;
        if (result == OUTCOME_STABLE || result == OUTCOME_REVERSAL) {
            recordRecentDirectionalOutcome(reason, result);
        }
    }

    int count(String releaseReason, String outcome) {
        int reason = reasonIndex(releaseReason);
        int result = outcomeIndex(outcome);
        if (reason < 0 || result < 0) return 0;
        return counts[reason][result];
    }

    int reversalRateSampleCount(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return recentDirectionalSize[reason];
    }

    int reversalRatePercent(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return -1;
        return reversalRatePercent(reason);
    }

    String reversalSignalLabel(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return "-";
        int samples = recentDirectionalSize[reason];
        if (samples < MIN_REVERSAL_RATE_SAMPLES) return "learn";
        int reversalRate = reversalRatePercent(reason);
        if (reversalRate <= STABLE_REVERSAL_RATE_MAX_PERCENT) return "stable";
        if (reversalRate >= HIGH_REVERSAL_RISK_MIN_PERCENT) return "risk";
        return "mixed";
    }

    void clear() {
        for (int reason = 0; reason < REASON_COUNT; reason++) {
            for (int outcome = 0; outcome < OUTCOME_COUNT; outcome++) {
                counts[reason][outcome] = 0;
            }
            recentDirectionalNext[reason] = 0;
            recentDirectionalSize[reason] = 0;
            recentDirectionalReversals[reason] = 0;
            for (int sample = 0; sample < REVERSAL_RATE_WINDOW_SAMPLES; sample++) {
                recentDirectionalOutcomes[reason][sample] = OUTCOME_STABLE;
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
            int directionalSamples = recentDirectionalSize[reason];
            if (directionalSamples >= MIN_REVERSAL_RATE_SAMPLES) {
                out.append(" rev").append(reversalRatePercent(reason)).append("%@")
                        .append(directionalSamples);
            }
            out.append(" sig=").append(reversalSignalLabel(reason));
        }
        return out.toString();
    }

    private int reversalRatePercent(int reason) {
        int samples = recentDirectionalSize[reason];
        if (samples < MIN_REVERSAL_RATE_SAMPLES) return -1;
        return (int) (((long) recentDirectionalReversals[reason] * 100L) / samples);
    }

    private String reversalSignalLabel(int reason) {
        int samples = recentDirectionalSize[reason];
        if (samples < MIN_REVERSAL_RATE_SAMPLES) return "learn";
        int reversalRate = reversalRatePercent(reason);
        if (reversalRate <= STABLE_REVERSAL_RATE_MAX_PERCENT) return "stable";
        if (reversalRate >= HIGH_REVERSAL_RISK_MIN_PERCENT) return "risk";
        return "mixed";
    }

    private void recordRecentDirectionalOutcome(int reason, int outcome) {
        int next = recentDirectionalNext[reason];
        if (recentDirectionalSize[reason] == REVERSAL_RATE_WINDOW_SAMPLES) {
            if (recentDirectionalOutcomes[reason][next] == OUTCOME_REVERSAL) {
                recentDirectionalReversals[reason]--;
            }
        } else {
            recentDirectionalSize[reason]++;
        }
        recentDirectionalOutcomes[reason][next] = outcome;
        if (outcome == OUTCOME_REVERSAL) recentDirectionalReversals[reason]++;
        recentDirectionalNext[reason] = (next + 1) % REVERSAL_RATE_WINDOW_SAMPLES;
    }

    private int reasonTotal(int reason) {
        long total = (long) counts[reason][OUTCOME_STABLE]
                + counts[reason][OUTCOME_REVERSAL]
                + counts[reason][OUTCOME_EXPIRED];
        return (int) Math.min(Integer.MAX_VALUE, total);
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
