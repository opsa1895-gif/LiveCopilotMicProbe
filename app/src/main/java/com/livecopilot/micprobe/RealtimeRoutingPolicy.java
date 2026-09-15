package com.livecopilot.micprobe;

final class RealtimeRoutingPolicy {
    static final long SECOND_UNSTABLE_BLOCK_MS = 3_000L;
    static final long THIRD_UNSTABLE_BLOCK_MS = 8_000L;
    static final long MAX_UNSTABLE_BLOCK_MS = 15_000L;
    static final long FIRST_OUTCOME_BLOCK_MS = 2_000L;
    static final long SECOND_OUTCOME_BLOCK_MS = 5_000L;
    static final long THIRD_OUTCOME_BLOCK_MS = 10_000L;
    static final long MAX_OUTCOME_BLOCK_MS = 15_000L;
    static final long BAD_FILE_PROBE_MS = 1_000L;
    static final long FAST_REALTIME_LATENCY_MS = 1_500L;
    static final long SLOW_REALTIME_LATENCY_MS = 3_000L;
    static final long VERY_SLOW_REALTIME_LATENCY_MS = 5_000L;
    static final long PENALTY_DECAY_STEP_MS = 15_000L;
    static final long ROUTE_LATENCY_SAMPLE_MAX_AGE_MS = 20_000L;
    static final long ROUTE_LATENCY_FILE_MARGIN_MS = 700L;
    static final int MAX_OUTCOME_PENALTY = 4;
    static final int MAX_LATENCY_PENALTY = 4;
    static final int GOOD_REALTIME_RESET_STREAK = 2;
    static final int MIN_ROUTE_LATENCY_SAMPLES = 2;
    static final int MAX_ROUTE_LATENCY_SAMPLES = 8;

    private RealtimeRoutingPolicy() {}

    static boolean isQuickFailure(long readyAtMs, long failedAtMs, long stableWindowMs) {
        return readyAtMs > 0L
                && failedAtMs >= readyAtMs
                && stableWindowMs > 0L
                && failedAtMs - readyAtMs < stableWindowMs;
    }

    static long blockMsForUnstableStreak(int unstableStreak) {
        if (unstableStreak < 2) return 0L;
        if (unstableStreak == 2) return SECOND_UNSTABLE_BLOCK_MS;
        if (unstableStreak == 3) return THIRD_UNSTABLE_BLOCK_MS;
        return MAX_UNSTABLE_BLOCK_MS;
    }

    static int nextOutcomePenalty(int currentPenalty, boolean acceptedUseful,
                                  boolean fileRecovery) {
        int safe = clampPenalty(currentPenalty, MAX_OUTCOME_PENALTY);
        if (fileRecovery) {
            int delta = acceptedUseful ? 1 : 2;
            return Math.min(MAX_OUTCOME_PENALTY, safe + delta);
        }
        if (!acceptedUseful) {
            return Math.min(MAX_OUTCOME_PENALTY, safe + 1);
        }
        return Math.max(0, safe - 2);
    }

    static int nextLatencyPenalty(int currentPenalty, long latencyMs) {
        int safe = clampPenalty(currentPenalty, MAX_LATENCY_PENALTY);
        if (latencyMs < 0L) return safe;
        if (latencyMs <= FAST_REALTIME_LATENCY_MS) return Math.max(0, safe - 1);
        if (latencyMs <= SLOW_REALTIME_LATENCY_MS) return safe;
        if (latencyMs <= VERY_SLOW_REALTIME_LATENCY_MS) {
            return Math.min(MAX_LATENCY_PENALTY, safe + 1);
        }
        return Math.min(MAX_LATENCY_PENALTY, safe + 2);
    }

    static boolean isSlowRealtimeLatency(long latencyMs) {
        return latencyMs > SLOW_REALTIME_LATENCY_MS;
    }

    static long nextRouteLatencyEstimate(long currentEstimateMs, int currentSamples,
                                         long sampleMs) {
        if (sampleMs < 0L) return currentEstimateMs;
        if (currentEstimateMs < 0L || currentSamples <= 0) return sampleMs;
        return Math.max(0L, (currentEstimateMs * 3L + sampleMs + 2L) / 4L);
    }

    static int nextRouteLatencySampleCount(int currentSamples, long sampleMs) {
        int safe = Math.max(0, Math.min(MAX_ROUTE_LATENCY_SAMPLES, currentSamples));
        if (sampleMs < 0L) return safe;
        return Math.min(MAX_ROUTE_LATENCY_SAMPLES, safe + 1);
    }

    static boolean shouldPreferFileForLatency(
            long realtimeEstimateMs, int realtimeSamples, long realtimeSampleAtMs,
            long fileEstimateMs, int fileSamples, long fileSampleAtMs, long nowMs) {
        if (realtimeEstimateMs < 0L || fileEstimateMs < 0L) return false;
        if (realtimeSamples < MIN_ROUTE_LATENCY_SAMPLES
                || fileSamples < MIN_ROUTE_LATENCY_SAMPLES) return false;
        if (!isRouteLatencyFresh(realtimeSampleAtMs, nowMs)
                || !isRouteLatencyFresh(fileSampleAtMs, nowMs)) return false;
        return realtimeEstimateMs - fileEstimateMs >= ROUTE_LATENCY_FILE_MARGIN_MS;
    }

    private static boolean isRouteLatencyFresh(long sampleAtMs, long nowMs) {
        return sampleAtMs > 0L
                && nowMs >= sampleAtMs
                && nowMs - sampleAtMs <= ROUTE_LATENCY_SAMPLE_MAX_AGE_MS;
    }

    static int decayedPenalty(int currentPenalty, long idleMs, int maxPenalty) {
        int safe = clampPenalty(currentPenalty, maxPenalty);
        if (safe <= 0 || idleMs < PENALTY_DECAY_STEP_MS) return safe;
        long steps = idleMs / PENALTY_DECAY_STEP_MS;
        if (steps >= safe) return 0;
        return safe - (int) steps;
    }

    static int nextGoodRealtimeStreak(int currentStreak, boolean acceptedUseful,
                                      boolean fileRecovery, long latencyMs) {
        if (!acceptedUseful || fileRecovery || latencyMs < 0L
                || latencyMs > SLOW_REALTIME_LATENCY_MS) return 0;
        return Math.min(GOOD_REALTIME_RESET_STREAK, Math.max(0, currentStreak) + 1);
    }

    static boolean shouldResetAfterGoodRealtime(int goodRealtimeStreak) {
        return goodRealtimeStreak >= GOOD_REALTIME_RESET_STREAK;
    }

    static int effectiveOutcomePenalty(int qualityPenalty, int latencyPenalty) {
        return Math.max(clampPenalty(qualityPenalty, MAX_OUTCOME_PENALTY),
                clampPenalty(latencyPenalty, MAX_LATENCY_PENALTY));
    }

    static long blockMsForOutcomePenalty(int penalty) {
        if (penalty <= 0) return 0L;
        if (penalty == 1) return FIRST_OUTCOME_BLOCK_MS;
        if (penalty == 2) return SECOND_OUTCOME_BLOCK_MS;
        if (penalty == 3) return THIRD_OUTCOME_BLOCK_MS;
        return MAX_OUTCOME_BLOCK_MS;
    }

    static int penaltyAfterBadFile(int currentPenalty) {
        return Math.max(0, Math.min(MAX_OUTCOME_PENALTY, currentPenalty) - 1);
    }

    static long shortenOutcomeBlockAfterBadFile(long nowMs, long outcomeBlockedUntilMs) {
        if (outcomeBlockedUntilMs <= nowMs) return outcomeBlockedUntilMs;
        return Math.min(outcomeBlockedUntilMs, nowMs + BAD_FILE_PROBE_MS);
    }

    static long effectiveBlockedUntil(long transportBlockedUntilMs, long outcomeBlockedUntilMs) {
        return Math.max(Math.max(0L, transportBlockedUntilMs),
                Math.max(0L, outcomeBlockedUntilMs));
    }

    static boolean isUsableFileOutcome(boolean hasFocus, int submittedChunks,
                                       int failedChunks, boolean lastChunkFailed) {
        return hasFocus
                && submittedChunks > 0
                && !lastChunkFailed
                && FileTurnCoveragePolicy.coveragePercent(submittedChunks, failedChunks)
                >= FileTurnCoveragePolicy.MIN_CONSERVATIVE_COVERAGE_PERCENT;
    }

    static boolean isFilePerformanceEligible(boolean usable, int degradedTurnStreak,
                                             long retryCooldownRemainingMs) {
        return usable && degradedTurnStreak <= 0 && retryCooldownRemainingMs <= 0L;
    }

    static boolean shouldUseRealtime(boolean ready, boolean socketPresent,
                                     long nowMs, long blockedUntilMs) {
        return ready
                && socketPresent
                && (blockedUntilMs <= 0L || nowMs >= blockedUntilMs);
    }

    private static int clampPenalty(int value, int max) {
        return Math.max(0, Math.min(Math.max(0, max), value));
    }
}
