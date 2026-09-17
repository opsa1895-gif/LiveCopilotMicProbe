package com.livecopilot.micprobe;

final class RouteReleaseOutcomeStats {
    static final int MIN_REVERSAL_RATE_SAMPLES = 3;
    static final int REVERSAL_RATE_WINDOW_SAMPLES = 8;
    static final int STABLE_REVERSAL_RATE_MAX_PERCENT = 25;
    static final int HIGH_REVERSAL_RISK_MIN_PERCENT = 50;
    static final int SIGNAL_CHANGE_CONFIRM_OUTCOMES = 2;
    static final int MIN_TRANSITION_CONFIRMATION_RATE_SAMPLES = 2;
    static final int TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES = 8;
    static final int STRONG_TRANSITION_CONFIRMATION_RATE_MIN_PERCENT = 75;
    static final int WEAK_TRANSITION_CONFIRMATION_RATE_MAX_PERCENT = 25;
    static final int RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS = 2;
    static final int MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES = 2;
    static final int RELIABILITY_TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES = 8;

    private static final int REASON_RT_SLOWDOWN = 0;
    private static final int REASON_RT_SPEEDUP = 1;
    private static final int REASON_FILE_SPEEDUP = 2;
    private static final int REASON_FILE_SLOWDOWN = 3;
    private static final int REASON_COUNT = 4;

    private static final int OUTCOME_STABLE = 0;
    private static final int OUTCOME_REVERSAL = 1;
    private static final int OUTCOME_EXPIRED = 2;
    private static final int OUTCOME_SUPERSEDED = 3;
    private static final int OUTCOME_COUNT = 4;

    private static final int TRANSITION_REVERTED = 0;
    private static final int TRANSITION_CONFIRMED = 1;

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
    private final int[][] recentTransitionResolutions =
            new int[REASON_COUNT][TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES];
    private final int[] recentTransitionResolutionNext = new int[REASON_COUNT];
    private final int[] recentTransitionResolutionSize = new int[REASON_COUNT];
    private final int[] recentTransitionConfirmations = new int[REASON_COUNT];
    private final String[] latchedTransitionReliabilityLabels = new String[REASON_COUNT];
    private final String[] candidateTransitionReliabilityLabels = new String[REASON_COUNT];
    private final int[] candidateTransitionReliabilityStreak = new int[REASON_COUNT];
    private final int[] confirmedTransitionReliabilityChanges = new int[REASON_COUNT];
    private final int[] canceledTransitionReliabilityChanges = new int[REASON_COUNT];
    private final int[] revertedTransitionReliabilityChanges = new int[REASON_COUNT];
    private final int[] supersededTransitionReliabilityChanges = new int[REASON_COUNT];
    private final RecentConfirmationRateWindow[] recentReliabilityTransitionRates =
            new RecentConfirmationRateWindow[REASON_COUNT];

    RouteReleaseOutcomeStats() {
        for (int reason = 0; reason < REASON_COUNT; reason++) {
            recentReliabilityTransitionRates[reason] = new RecentConfirmationRateWindow(
                    RELIABILITY_TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES);
        }
    }

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

    String transitionConfirmationReliabilityLabel(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return "-";
        return transitionConfirmationReliabilityLabel(reason);
    }

    String transitionConfirmationReliabilityPendingLabel(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return "-";
        String candidate = candidateTransitionReliabilityLabels[reason];
        return candidate != null ? candidate : "-";
    }

    int transitionConfirmationReliabilityPendingStreak(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return candidateTransitionReliabilityStreak[reason];
    }

    int confirmedTransitionReliabilityChangeCount(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return confirmedTransitionReliabilityChanges[reason];
    }

    int canceledTransitionReliabilityChangeCount(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return canceledTransitionReliabilityChanges[reason];
    }

    int revertedTransitionReliabilityChangeCount(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return revertedTransitionReliabilityChanges[reason];
    }

    int supersededTransitionReliabilityChangeCount(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return supersededTransitionReliabilityChanges[reason];
    }

    int reliabilityTransitionConfirmationRateSampleCount(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return 0;
        return recentReliabilityTransitionRates[reason].sampleCount();
    }

    int reliabilityTransitionConfirmationRatePercent(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return -1;
        return recentReliabilityTransitionRates[reason].confirmationRatePercent(
                MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES);
    }

    String reliabilityTransitionSampleMaturityLabel(String releaseReason) {
        int reason = reasonIndex(releaseReason);
        if (reason < 0) return "-";
        return reliabilityTransitionSampleMaturityLabelForSamples(
                recentReliabilityTransitionRates[reason].sampleCount());
    }

    static int reliabilityTransitionConfirmationRateSampleCountForCounts(
            int confirmed, int reverted) {
        long samples = (long) Math.max(0, confirmed) + Math.max(0, reverted);
        return samples >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) samples;
    }

    static int reliabilityTransitionConfirmationRatePercentForCounts(
            int confirmed, int reverted) {
        int samples = reliabilityTransitionConfirmationRateSampleCountForCounts(
                confirmed, reverted);
        if (samples < MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) return -1;
        return (int) (((long) Math.max(0, confirmed) * 100L) / samples);
    }

    static String reliabilityTransitionSampleMaturityLabelForSamples(int samples) {
        int safeSamples = Math.max(0, samples);
        if (safeSamples < MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) {
            return "low";
        }
        if (safeSamples < RELIABILITY_TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES) {
            return "usable";
        }
        return "mature";
    }

    static String transitionReliabilityCancellationReason(
            String latchedLabel, String candidateLabel, String rawLabel) {
        if (latchedLabel == null || candidateLabel == null || rawLabel == null) return "-";
        if (rawLabel.equals(latchedLabel)) return "reverted";
        if (!rawLabel.equals(candidateLabel)) return "superseded";
        return "-";
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
            recentTransitionResolutionNext[reason] = 0;
            recentTransitionResolutionSize[reason] = 0;
            recentTransitionConfirmations[reason] = 0;
            latchedTransitionReliabilityLabels[reason] = null;
            candidateTransitionReliabilityLabels[reason] = null;
            candidateTransitionReliabilityStreak[reason] = 0;
            confirmedTransitionReliabilityChanges[reason] = 0;
            canceledTransitionReliabilityChanges[reason] = 0;
            revertedTransitionReliabilityChanges[reason] = 0;
            supersededTransitionReliabilityChanges[reason] = 0;
            recentReliabilityTransitionRates[reason].clear();
            for (int sample = 0; sample < REVERSAL_RATE_WINDOW_SAMPLES; sample++) {
                recentDirectionalOutcomes[reason][sample] = OUTCOME_STABLE;
            }
            for (int sample = 0; sample < TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES; sample++) {
                recentTransitionResolutions[reason][sample] = TRANSITION_REVERTED;
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
            if (counts[reason][OUTCOME_SUPERSEDED] > 0) {
                out.append(" sup=").append(counts[reason][OUTCOME_SUPERSEDED]);
            }
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
                if (confirmationSamples >= MIN_TRANSITION_CONFIRMATION_RATE_SAMPLES) {
                    out.append(" conf").append(transitionConfirmationRatePercent(reason)).append("%@")
                            .append(confirmationSamples).append('/')
                            .append(transitionConfirmationReliabilityLabel(reason));
                    if (candidateTransitionReliabilityLabels[reason] != null
                            && candidateTransitionReliabilityStreak[reason] > 0) {
                        out.append('>').append(candidateTransitionReliabilityLabels[reason]).append('×')
                                .append(candidateTransitionReliabilityStreak[reason]).append('/')
                                .append(RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS);
                    }
                    if (confirmedTransitionReliabilityChanges[reason] > 0
                            || canceledTransitionReliabilityChanges[reason] > 0) {
                        out.append(" rtr=").append(confirmedTransitionReliabilityChanges[reason])
                                .append('/').append(canceledTransitionReliabilityChanges[reason]);
                        if (canceledTransitionReliabilityChanges[reason] > 0) {
                            out.append(" rcancel=").append(revertedTransitionReliabilityChanges[reason])
                                    .append('/').append(supersededTransitionReliabilityChanges[reason]);
                        }
                        int reliabilityTransitionSamples =
                                recentReliabilityTransitionRates[reason].sampleCount();
                        out.append(" rmat=")
                                .append(reliabilityTransitionSampleMaturityLabelForSamples(
                                        reliabilityTransitionSamples));
                        if (reliabilityTransitionSamples
                                >= MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) {
                            out.append(" rconf")
                                    .append(recentReliabilityTransitionRates[reason]
                                            .confirmationRatePercent(
                                                    MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES))
                                    .append("%@").append(reliabilityTransitionSamples);
                        }
                    }
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
        return recentTransitionResolutionSize[reason];
    }

    private int transitionConfirmationRatePercent(int reason) {
        int samples = recentTransitionResolutionSize[reason];
        if (samples <= 0) return -1;
        return (int) (((long) recentTransitionConfirmations[reason] * 100L) / samples);
    }

    static String transitionConfirmationReliabilityLabelForRate(
            int samples, int confirmationRatePercent) {
        if (samples < MIN_TRANSITION_CONFIRMATION_RATE_SAMPLES) return "learn";
        if (confirmationRatePercent >= STRONG_TRANSITION_CONFIRMATION_RATE_MIN_PERCENT) {
            return "strong";
        }
        if (confirmationRatePercent <= WEAK_TRANSITION_CONFIRMATION_RATE_MAX_PERCENT) {
            return "weak";
        }
        return "mixed";
    }

    private String transitionConfirmationReliabilityLabel(int reason) {
        int samples = transitionConfirmationRateSampleCount(reason);
        if (samples < MIN_TRANSITION_CONFIRMATION_RATE_SAMPLES) return "learn";
        String latched = latchedTransitionReliabilityLabels[reason];
        return latched != null ? latched : rawTransitionConfirmationReliabilityLabel(reason);
    }

    private String rawTransitionConfirmationReliabilityLabel(int reason) {
        return transitionConfirmationReliabilityLabelForRate(
                transitionConfirmationRateSampleCount(reason),
                transitionConfirmationRatePercent(reason));
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

    private void recordRecentTransitionResolution(int reason, int resolution) {
        int next = recentTransitionResolutionNext[reason];
        if (recentTransitionResolutionSize[reason] == TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES) {
            if (recentTransitionResolutions[reason][next] == TRANSITION_CONFIRMED) {
                recentTransitionConfirmations[reason]--;
            }
        } else {
            recentTransitionResolutionSize[reason]++;
        }
        recentTransitionResolutions[reason][next] = resolution;
        if (resolution == TRANSITION_CONFIRMED) recentTransitionConfirmations[reason]++;
        recentTransitionResolutionNext[reason] =
                (next + 1) % TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES;
        updateTransitionReliabilityLabel(reason);
    }

    private void updateTransitionReliabilityLabel(int reason) {
        if (recentTransitionResolutionSize[reason] < MIN_TRANSITION_CONFIRMATION_RATE_SAMPLES) {
            latchedTransitionReliabilityLabels[reason] = null;
            candidateTransitionReliabilityLabels[reason] = null;
            candidateTransitionReliabilityStreak[reason] = 0;
            return;
        }

        String rawLabel = rawTransitionConfirmationReliabilityLabel(reason);
        String latchedLabel = latchedTransitionReliabilityLabels[reason];
        if (latchedLabel == null) {
            latchedTransitionReliabilityLabels[reason] = rawLabel;
            candidateTransitionReliabilityLabels[reason] = null;
            candidateTransitionReliabilityStreak[reason] = 0;
            return;
        }
        String cancellationReason = transitionReliabilityCancellationReason(
                latchedLabel, candidateTransitionReliabilityLabels[reason], rawLabel);
        if (rawLabel.equals(latchedLabel)) {
            if ("reverted".equals(cancellationReason)) {
                incrementRevertedTransitionReliabilityChange(reason);
            }
            candidateTransitionReliabilityLabels[reason] = null;
            candidateTransitionReliabilityStreak[reason] = 0;
            return;
        }
        if (rawLabel.equals(candidateTransitionReliabilityLabels[reason])) {
            candidateTransitionReliabilityStreak[reason]++;
        } else {
            if ("superseded".equals(cancellationReason)) {
                incrementSupersededTransitionReliabilityChange(reason);
            }
            candidateTransitionReliabilityLabels[reason] = rawLabel;
            candidateTransitionReliabilityStreak[reason] = 1;
        }
        if (candidateTransitionReliabilityStreak[reason] >= RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS) {
            latchedTransitionReliabilityLabels[reason] = rawLabel;
            incrementConfirmedTransitionReliabilityChange(reason);
            candidateTransitionReliabilityLabels[reason] = null;
            candidateTransitionReliabilityStreak[reason] = 0;
        }
    }

    private void incrementConfirmedTransitionReliabilityChange(int reason) {
        if (confirmedTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {
            confirmedTransitionReliabilityChanges[reason]++;
        }
        recentReliabilityTransitionRates[reason].recordConfirmed();
    }

    private void incrementRevertedTransitionReliabilityChange(int reason) {
        if (canceledTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {
            canceledTransitionReliabilityChanges[reason]++;
        }
        if (revertedTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {
            revertedTransitionReliabilityChanges[reason]++;
        }
        recentReliabilityTransitionRates[reason].recordReverted();
    }

    private void incrementSupersededTransitionReliabilityChange(int reason) {
        if (canceledTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {
            canceledTransitionReliabilityChanges[reason]++;
        }
        if (supersededTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {
            supersededTransitionReliabilityChanges[reason]++;
        }
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
        recordRecentTransitionResolution(reason, TRANSITION_CONFIRMED);
    }

    private void incrementRevertedSignalTransition(int reason) {
        incrementCanceledSignalTransition(reason);
        if (revertedSignalTransitions[reason] < Integer.MAX_VALUE) {
            revertedSignalTransitions[reason]++;
        }
        recordRecentTransitionResolution(reason, TRANSITION_REVERTED);
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
                + counts[reason][OUTCOME_EXPIRED]
                + counts[reason][OUTCOME_SUPERSEDED];
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
        if ("superseded".equals(outcome)) return OUTCOME_SUPERSEDED;
        return -1;
    }
}
