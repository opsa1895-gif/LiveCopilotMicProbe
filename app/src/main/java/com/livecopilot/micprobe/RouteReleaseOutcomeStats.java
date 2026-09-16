package com.livecopilot.micprobe;

final class RouteReleaseOutcomeStats {
    static final int MIN_REVERSAL_RATE_SAMPLES = 3;
    static final int REVERSAL_RATE_WINDOW_SAMPLES = 8;
    static final int STABLE_REVERSAL_RATE_MAX_PERCENT = 25;
    static final int HIGH_REVERSAL_RISK_MIN_PERCENT = 50;
    static final int SIGNAL_CHANGE_CONFIRM_OUTCOMES = 2;

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
    private final String[] latchedSignalLabels = new String[REASON_COUNT];
    private final String[] candidateSignalLabels = new String[REASON_COUNT];
    private final int[] candidateSignalStreak = new int[REASON_COUNT];
    private final int[] confirmedSignalTransitions = new int[REASON_COUNT];
    private final int[] canceledSignalTransitions = new int[REASON_COUNT];
    private final int[] revertedSignalTransitions = new int[REASON_COUNT];
    private final int[] supersededSignalTransitions = new int[REASON_COUNT];

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
        return reversalSignalLabel(reason);
    }

    String reversalSignalPendingLabel(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return "-";
        String candidate = candidateSignalLabels[reason];
        return candidate != null ? candidate : "-";
    }

    int reversalSignalPendingStreak(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return candidateSignalStreak[reason];
    }

    int confirmedSignalTransitionCount(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return confirmedSignalTransitions[reason];
    }

    int canceledSignalTransitionCount(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return canceledSignalTransitions[reason];
    }

    int revertedSignalTransitionCount(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return revertedSignalTransitions[reason];
    }

    int supersededSignalTransitionCount(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return supersededSignalTransitions[reason];
    }

    int transitionConfirmationRateSampleCount(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return transitionConfirmationRateSampleCount(reason);
    }

    int transitionConfirmationRatePercent(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return -1;
        return transitionConfirmationRatePercent(reason);
    }

    void clear() {
        for (int reason = 0; reason < REASON_COUNT; reason++) {
            for (int outcome = 0; outcome < OUTCOME_COUNT; outcome++) {
                counts[reason][outcome] = 0;
            }
            recentDirectionalNext[reason] = 0;
            recentDirectionalSize[reason] = 0;
            recentDirectionalReversals[reason] = 0;
            latchedSignalLabels[reason] = null;
            candidateSignalLabels[reason] = null;
            candidateSignalStreak[reason] = 0;
            confirmedSignalTransitions[reason] = 0;
            canceledSignalTransitions[reason] = 0;
            revertedSignalTransitions[reason] = 0;
            supersededSignalTransitions[reason] = 0;
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
            if (candidateSignalLabels[reason] != null && candidateSignalStreak[reason] > 0) {
                out.append('>').append(candidateSignalLabels[reason]).append('×')
                        .append(candidateSignalStreak[reason]).append('/')
                        .append(SIGNAL_CHANGE_CONFIRM_OUTCOMES);
            }
            if (confirmedSignalTransitions[reason] > 0 || canceledSignalTransitions[reason] > 0) {
                out.append(" tr=").append(confirmedSignalTransitions[reason]).append('/')
                        .append(canceledSignalTransitions[reason]);
                if (canceledSignalTransitions[reason] > 0) {
                    out.append(" cancel=").append(revertedSignalTransitions[reason]).append('/')
                            .append(supersededSignalTransitions[reason]);
                }
                int confirmationSamples = transitionConfirmationRateSampleCount(reason);
                if (confirmationSamples > 0) {
                    out.append(" conf").append(transitionConfirmationRatePercent(reason)).append("%@")
                            .append(confirmationSamples);
                }
            }
        }
        return out.toString();
    }

    private int reversalRatePercent(int reason) {
        int samples = recentDirectionalSize[reason];
        if (samples < MIN_REVERSAL_RATE_SAMPLES) return -1;
        return (int) (((long) recentDirectionalReversals[reason] * 100L) / samples);
    }

    private int transitionConfirmationRateSampleCount(int reason) {
        long samples = (long) confirmedSignalTransitions[reason] + revertedSignalTransitions[reason];
        return (int) Math.min(Integer.MAX_VALUE, samples);
    }

    private int transitionConfirmationRatePercent(int reason) {
        long samples = (long) confirmedSignalTransitions[reason] + revertedSignalTransitions[reason];
        if (samples <= 0L) return -1;
        return (int) (((long) confirmedSignalTransitions[reason] * 100L) / samples);
    }

    private String reversalSignalLabel(int reason) {
        if (recentDirectionalSize[reason] < MIN_REVERSAL_RATE_SAMPLES) return "learn";
        String latched = latchedSignalLabels[reason];
        return latched != null ? latched : rawReversalSignalLabel(reason);
    }

    private String rawReversalSignalLabel(int reason) {
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
        updateSignalLabel(reason);
    }

    private void updateSignalLabel(int reason) {
        if (recentDirectionalSize[reason] < MIN_REVERSAL_RATE_SAMPLES) {
            latchedSignalLabels[reason] = null;
            candidateSignalLabels[reason] = null;
            candidateSignalStreak[reason] = 0;
            return;
        }

        String rawLabel = rawReversalSignalLabel(reason);
        String latchedLabel = latchedSignalLabels[reason];
        if (latchedLabel == null) {
            latchedSignalLabels[reason] = rawLabel;
            candidateSignalLabels[reason] = null;
            candidateSignalStreak[reason] = 0;
            return;
        }
        if (rawLabel.equals(latchedLabel)) {
            if (candidateSignalLabels[reason] != null) {
                incrementRevertedSignalTransition(reason);
            }
            candidateSignalLabels[reason] = null;
            candidateSignalStreak[reason] = 0;
            return;
        }
        if (rawLabel.equals(candidateSignalLabels[reason])) {
            candidateSignalStreak[reason]++;
        } else {
            if (candidateSignalLabels[reason] != null) {
                incrementSupersededSignalTransition(reason);
            }
            candidateSignalLabels[reason] = rawLabel;
            candidateSignalStreak[reason] = 1;
        }
        if (candidateSignalStreak[reason] >= SIGNAL_CHANGE_CONFIRM_OUTCOMES) {
            latchedSignalLabels[reason] = rawLabel;
            incrementConfirmedSignalTransition(reason);
            candidateSignalLabels[reason] = null;
            candidateSignalStreak[reason] = 0;
        }
    }

    private void incrementConfirmedSignalTransition(int reason) {
        if (confirmedSignalTransitions[reason] < Integer.MAX_VALUE) {
            confirmedSignalTransitions[reason]++;
        }
    }

    private void incrementRevertedSignalTransition(int reason) {
        incrementCanceledSignalTransition(reason);
        if (revertedSignalTransitions[reason] < Integer.MAX_VALUE) {
            revertedSignalTransitions[reason]++;
        }
    }

    private void incrementSupersededSignalTransition(int reason) {
        incrementCanceledSignalTransition(reason);
        if (supersededSignalTransitions[reason] < Integer.MAX_VALUE) {
            supersededSignalTransitions[reason]++;
        }
    }

    private void incrementCanceledSignalTransition(int reason) {
        if (canceledSignalTransitions[reason] < Integer.MAX_VALUE) {
            canceledSignalTransitions[reason]++;
        }
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
