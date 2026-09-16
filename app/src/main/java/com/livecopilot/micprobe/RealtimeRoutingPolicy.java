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
    static final long ROUTE_LATENCY_CONFIDENCE_MAX_EXTRA_MARGIN_MS = 1_200L;
    static final long ROUTE_LATENCY_MIN_SAMPLE_EXTRA_MARGIN_MS = 100L;
    static final long ROUTE_LATENCY_SWITCH_GUARD_MS = 2_000L;
    static final long ROUTE_LATENCY_SWITCH_EXTRA_MARGIN_MS = 300L;
    static final long ROUTE_LATENCY_SWITCH_MIN_GUARD_MS = 1_500L;
    static final long ROUTE_LATENCY_SWITCH_MAX_GUARD_MS = 4_000L;
    static final long ROUTE_LATENCY_SWITCH_MODERATE_JITTER_EXTRA_MS = 500L;
    static final long ROUTE_LATENCY_SWITCH_HIGH_JITTER_EXTRA_MS = 1_500L;
    static final long ROUTE_LATENCY_SWITCH_MIN_SAMPLE_EXTRA_MS = 500L;
    static final long ROUTE_LATENCY_SWITCH_STABLE_REDUCTION_MS = 500L;
    static final long ROUTE_LATENCY_SWITCH_MIN_EXTRA_MARGIN_MS = 200L;
    static final long ROUTE_LATENCY_SWITCH_MAX_EXTRA_MARGIN_MS = 500L;
    static final long ROUTE_LATENCY_SWITCH_LOW_CONFIDENCE_EXTRA_MARGIN_MS = 200L;
    static final long ROUTE_FLAP_WINDOW_MS = 20_000L;
    static final long ROUTE_FLAP_MARGIN_STEP_MS = 200L;
    static final long ROUTE_FLAP_MAX_EXTRA_MARGIN_MS = 600L;
    static final int ROUTE_FLAP_MAX_SCORE = 3;
    static final int ROUTE_FLAP_STABLE_CONFIRM_TURNS = 3;
    static final int ROUTE_PERFORMANCE_ROUTE_UNKNOWN = -1;
    static final int ROUTE_PERFORMANCE_ROUTE_REALTIME = 0;
    static final int ROUTE_PERFORMANCE_ROUTE_FILE = 1;
    static final int ROUTE_LATENCY_FULL_CONFIDENCE_SAMPLES = 4;
    static final int ROUTE_LATENCY_MATURE_SWITCH_SAMPLES = 6;
    static final long MAX_ROUTE_LATENCY_JITTER_MS = 5_000L;
    static final long MAX_ROUTE_LATENCY_SAMPLE_MS = 12_000L;
    static final long MAX_ROUTE_LATENCY_RISE_STEP_MS = 3_000L;
    static final long MAX_ROUTE_LATENCY_DROP_STEP_MS = 4_000L;
    static final int ROUTE_LATENCY_REGIME_CONFIRM_STREAK = 2;
    static final int ROUTE_LATENCY_SETTLE_SAMPLES = 2;
    static final long ROUTE_PROBE_MIN_MS = 6_000L;
    static final long ROUTE_PROBE_DEFAULT_MS = 12_000L;
    static final long ROUTE_PROBE_MAX_MS = 20_000L;
    static final long ROUTE_PROBE_EARLY_MARGIN_MS = 1_200L;
    static final long ROUTE_PROBE_STRONG_MARGIN_MS = 2_500L;
    static final long ROUTE_PROBE_HIGH_JITTER_MS = 1_500L;
    static final long ROUTE_PROBE_STABLE_JITTER_MS = 600L;
    static final long ROUTE_SWITCH_VERY_STABLE_JITTER_MS = 300L;
    static final int MAX_OUTCOME_PENALTY = 4;
    static final int MAX_LATENCY_PENALTY = 4;
    static final int GOOD_REALTIME_RESET_STREAK = 2;
    static final int MIN_ROUTE_LATENCY_SAMPLES = 3;
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

    static long boundedRouteLatencySample(long currentEstimateMs, int currentSamples,
                                          long sampleMs) {
        if (sampleMs < 0L) return sampleMs;
        long bounded = Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS, sampleMs);
        if (currentEstimateMs < 0L || currentSamples <= 0) return bounded;

        long safeEstimate = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS, currentEstimateMs));
        long floor = Math.max(0L, safeEstimate - MAX_ROUTE_LATENCY_DROP_STEP_MS);
        long ceiling = Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS,
                safeEstimate + MAX_ROUTE_LATENCY_RISE_STEP_MS);
        return Math.max(floor, Math.min(ceiling, bounded));
    }

    static boolean isRouteLatencyOutlier(long currentEstimateMs, int currentSamples,
                                         long sampleMs) {
        return sampleMs >= 0L
                && boundedRouteLatencySample(currentEstimateMs, currentSamples, sampleMs) != sampleMs;
    }

    static int routeLatencyOutlierDirection(long currentEstimateMs, int currentSamples,
                                            long sampleMs) {
        if (sampleMs < 0L || currentEstimateMs < 0L || currentSamples <= 0) return 0;
        long boundedSampleMs = boundedRouteLatencySample(
                currentEstimateMs, currentSamples, sampleMs);
        if (sampleMs > boundedSampleMs) return 1;
        if (sampleMs < boundedSampleMs) return -1;
        return 0;
    }

    static int nextRouteLatencyOutlierStreak(int currentDirection, int currentStreak,
                                             int nextDirection, boolean previousEvidenceFresh) {
        if (nextDirection == 0) {
            if (!previousEvidenceFresh) return 0;
            if (currentDirection != 0
                    && currentStreak >= ROUTE_LATENCY_REGIME_CONFIRM_STREAK) {
                return ROUTE_LATENCY_SETTLE_SAMPLES;
            }
            if (currentDirection == 0 && currentStreak > 0) {
                int safeSettle = Math.min(ROUTE_LATENCY_SETTLE_SAMPLES, currentStreak);
                return Math.max(0, safeSettle - 1);
            }
            return 0;
        }
        if (!previousEvidenceFresh || nextDirection != currentDirection) return 1;
        int safe = Math.max(0, Math.min(ROUTE_LATENCY_REGIME_CONFIRM_STREAK, currentStreak));
        return Math.min(ROUTE_LATENCY_REGIME_CONFIRM_STREAK, safe + 1);
    }

    static boolean isRouteLatencyRegimeChange(int direction, int outlierStreak) {
        return direction != 0 && outlierStreak >= ROUTE_LATENCY_REGIME_CONFIRM_STREAK;
    }

    static boolean isRouteLatencySettling(int direction, int outlierStreak) {
        return direction == 0
                && outlierStreak > 0
                && outlierStreak <= ROUTE_LATENCY_SETTLE_SAMPLES;
    }

    static long regimeAwareRouteLatencySample(long currentEstimateMs, int currentSamples,
                                              long sampleMs, int outlierDirection,
                                              int outlierStreak) {
        if (sampleMs < 0L) return sampleMs;
        if (isRouteLatencyRegimeChange(outlierDirection, outlierStreak)) {
            return Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS, sampleMs);
        }
        return boundedRouteLatencySample(currentEstimateMs, currentSamples, sampleMs);
    }

    static long nextRouteLatencyEstimate(long currentEstimateMs, int currentSamples,
                                         long sampleMs) {
        return nextRouteLatencyEstimate(currentEstimateMs, currentSamples, sampleMs, 0, 0);
    }

    static long nextRouteLatencyEstimate(long currentEstimateMs, int currentSamples,
                                         long sampleMs, int outlierDirection,
                                         int outlierStreak) {
        if (sampleMs < 0L) return currentEstimateMs;
        long boundedSampleMs = regimeAwareRouteLatencySample(
                currentEstimateMs, currentSamples, sampleMs, outlierDirection, outlierStreak);
        if (currentEstimateMs < 0L || currentSamples <= 0) return boundedSampleMs;
        long safeEstimate = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS, currentEstimateMs));
        if (isRouteLatencyRegimeChange(outlierDirection, outlierStreak)
                || isRouteLatencySettling(outlierDirection, outlierStreak)) {
            return routeLatencyRegimeMidpoint(safeEstimate, boundedSampleMs);
        }
        return Math.max(0L, (safeEstimate * 3L + boundedSampleMs + 2L) / 4L);
    }

    static int nextRouteLatencySampleCount(int currentSamples, long sampleMs) {
        int safe = Math.max(0, Math.min(MAX_ROUTE_LATENCY_SAMPLES, currentSamples));
        if (sampleMs < 0L) return safe;
        return Math.min(MAX_ROUTE_LATENCY_SAMPLES, safe + 1);
    }

    static boolean isRealtimePerformanceSampleEligible(
            boolean acceptedUseful, boolean fileRecovery, long latencyMs) {
        return acceptedUseful && !fileRecovery && latencyMs >= 0L;
    }

    static long nextRouteLatencyJitter(long currentJitterMs, long currentEstimateMs,
                                       int currentSamples, long sampleMs) {
        return nextRouteLatencyJitter(
                currentJitterMs, currentEstimateMs, currentSamples, sampleMs, 0, 0);
    }

    static long nextRouteLatencyJitter(long currentJitterMs, long currentEstimateMs,
                                       int currentSamples, long sampleMs,
                                       int outlierDirection, int outlierStreak) {
        if (sampleMs < 0L) return currentJitterMs;
        if (currentEstimateMs < 0L || currentSamples <= 0) return 0L;
        long safeEstimate = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS, currentEstimateMs));
        long boundedSampleMs = regimeAwareRouteLatencySample(
                safeEstimate, currentSamples, sampleMs, outlierDirection, outlierStreak);
        long deviation;
        if (isRouteLatencyRegimeChange(outlierDirection, outlierStreak)
                || isRouteLatencySettling(outlierDirection, outlierStreak)) {
            long rebasedEstimateMs = routeLatencyRegimeMidpoint(safeEstimate, boundedSampleMs);
            deviation = Math.abs(boundedSampleMs - rebasedEstimateMs);
        } else {
            deviation = Math.abs(boundedSampleMs - safeEstimate);
        }
        if (currentSamples <= 1 || currentJitterMs < 0L) {
            return Math.min(MAX_ROUTE_LATENCY_JITTER_MS, deviation);
        }
        long safeJitter = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_JITTER_MS, currentJitterMs));
        long next = (safeJitter * 3L + deviation + 2L) / 4L;
        return Math.min(MAX_ROUTE_LATENCY_JITTER_MS, Math.max(0L, next));
    }

    static long riskAdjustedRouteLatency(long estimateMs, long jitterMs) {
        if (estimateMs < 0L || jitterMs < 0L) return -1L;
        long safeJitter = Math.min(MAX_ROUTE_LATENCY_JITTER_MS, jitterMs);
        return estimateMs + Math.max(0L, safeJitter);
    }

    static long routeLatencyRiskGapMs(
            long realtimeEstimateMs, long realtimeJitterMs,
            long fileEstimateMs, long fileJitterMs) {
        long realtimeRiskMs = riskAdjustedRouteLatency(realtimeEstimateMs, realtimeJitterMs);
        long fileRiskMs = riskAdjustedRouteLatency(fileEstimateMs, fileJitterMs);
        if (realtimeRiskMs < 0L || fileRiskMs < 0L) return Long.MIN_VALUE;
        return realtimeRiskMs - fileRiskMs;
    }

    static long routeLatencyPreferenceMarginMs(
            long realtimeJitterMs, int realtimeSamples,
            long fileJitterMs, int fileSamples) {
        if (realtimeJitterMs < 0L || fileJitterMs < 0L
                || realtimeSamples < MIN_ROUTE_LATENCY_SAMPLES
                || fileSamples < MIN_ROUTE_LATENCY_SAMPLES) {
            return Long.MAX_VALUE;
        }
        long safeRealtimeJitterMs = Math.min(MAX_ROUTE_LATENCY_JITTER_MS, realtimeJitterMs);
        long safeFileJitterMs = Math.min(MAX_ROUTE_LATENCY_JITTER_MS, fileJitterMs);
        long combinedJitterMs = safeRealtimeJitterMs + safeFileJitterMs;
        long excessJitterMs = Math.max(0L, combinedJitterMs - ROUTE_PROBE_STABLE_JITTER_MS);
        long jitterExtraMarginMs = Math.min(
                ROUTE_LATENCY_CONFIDENCE_MAX_EXTRA_MARGIN_MS,
                (excessJitterMs + 1L) / 2L);

        int minimumSamples = Math.min(
                Math.min(MAX_ROUTE_LATENCY_SAMPLES, realtimeSamples),
                Math.min(MAX_ROUTE_LATENCY_SAMPLES, fileSamples));
        int sampleShortfall = Math.max(
                0, ROUTE_LATENCY_FULL_CONFIDENCE_SAMPLES - minimumSamples);
        long sampleExtraMarginMs = excessJitterMs > 0L
                ? sampleShortfall * ROUTE_LATENCY_MIN_SAMPLE_EXTRA_MARGIN_MS
                : 0L;
        return ROUTE_LATENCY_FILE_MARGIN_MS + jitterExtraMarginMs + sampleExtraMarginMs;
    }

    static boolean isRouteFlapHistoryFresh(long lastDecisionAtMs, long nowMs) {
        return lastDecisionAtMs > 0L
                && nowMs >= lastDecisionAtMs
                && nowMs - lastDecisionAtMs <= ROUTE_FLAP_WINDOW_MS;
    }

    static int activeRouteFlapScore(int currentScore, long lastDecisionAtMs, long nowMs) {
        int safe = Math.max(0, Math.min(ROUTE_FLAP_MAX_SCORE, currentScore));
        if (safe <= 0) return 0;
        return isRouteFlapHistoryFresh(lastDecisionAtMs, nowMs) ? safe : 0;
    }

    static int nextRouteFlapScore(int currentScore, int previousRoute,
                                  long previousDecisionAtMs, int nextRoute, long nowMs) {
        int safe = Math.max(0, Math.min(ROUTE_FLAP_MAX_SCORE, currentScore));
        boolean previousRouteKnown = previousRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || previousRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean nextRouteKnown = nextRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || nextRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean previousFresh = previousDecisionAtMs > 0L
                && nowMs >= previousDecisionAtMs
                && nowMs - previousDecisionAtMs <= ROUTE_FLAP_WINDOW_MS;
        if (!nextRouteKnown) return activeRouteFlapScore(safe, previousDecisionAtMs, nowMs);
        if (!previousRouteKnown || !previousFresh) return 0;
        if (nextRoute != previousRoute) {
            return Math.min(ROUTE_FLAP_MAX_SCORE, safe + 1);
        }
        return Math.max(0, safe - 1);
    }

    static int nextRouteFlapScoreFromHistory(
            int currentScore, int previousDistinctRoute, int currentRoute,
            long currentDecisionAtMs, int nextRoute, long nowMs) {
        int safe = Math.max(0, Math.min(ROUTE_FLAP_MAX_SCORE, currentScore));
        boolean currentRouteKnown = currentRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || currentRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean nextRouteKnown = nextRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || nextRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean currentFresh = currentDecisionAtMs > 0L
                && nowMs >= currentDecisionAtMs
                && nowMs - currentDecisionAtMs <= ROUTE_FLAP_WINDOW_MS;
        if (!nextRouteKnown) return activeRouteFlapScore(safe, currentDecisionAtMs, nowMs);
        if (!currentRouteKnown || !currentFresh) return 0;
        if (nextRoute == currentRoute) return Math.max(0, safe - 1);
        if (isRouteFlapReversal(previousDistinctRoute, currentRoute, nextRoute)) {
            return Math.min(ROUTE_FLAP_MAX_SCORE, safe + 1);
        }
        return safe;
    }

    static int nextStablePerformanceRouteStreak(
            int currentStreak, int currentRoute, int nextRoute) {
        boolean currentKnown = currentRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || currentRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean nextKnown = nextRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || nextRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        if (!nextKnown) return 0;
        if (!currentKnown || nextRoute != currentRoute) return 1;
        int safe = Math.max(1, Math.min(ROUTE_FLAP_STABLE_CONFIRM_TURNS, currentStreak));
        return Math.min(ROUTE_FLAP_STABLE_CONFIRM_TURNS, safe + 1);
    }

    static boolean shouldReleaseRouteFlapHistory(int stableRouteStreak) {
        return stableRouteStreak >= ROUTE_FLAP_STABLE_CONFIRM_TURNS;
    }

    static boolean shouldReleaseRouteFlapHistoryForRegimeChange(
            int routeFlapScore, int changedRoute, int outlierDirection, int outlierStreak,
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        if (routeFlapScore <= 0
                || !isRouteLatencyRegimeChange(outlierDirection, outlierStreak)) return false;
        boolean fileFavoringShift = changedRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                ? outlierDirection > 0
                : changedRoute == ROUTE_PERFORMANCE_ROUTE_FILE && outlierDirection < 0;
        boolean realtimeFavoringShift = changedRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                ? outlierDirection < 0
                : changedRoute == ROUTE_PERFORMANCE_ROUTE_FILE && outlierDirection > 0;
        if (!fileFavoringShift && !realtimeFavoringShift) return false;
        if (!isRouteLatencyFresh(realtimeSampleAtMs, nowMs)
                || !isRouteLatencyFresh(fileSampleAtMs, nowMs)) return false;

        long riskGapMs = routeLatencyRiskGapMs(
                realtimeEstimateMs, realtimeJitterMs, fileEstimateMs, fileJitterMs);
        long baselineMarginMs = routeLatencyPreferenceMarginMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
        if (riskGapMs == Long.MIN_VALUE || baselineMarginMs == Long.MAX_VALUE) return false;
        if (fileFavoringShift) return riskGapMs >= baselineMarginMs;
        return riskGapMs < baselineMarginMs;
    }

    static boolean isRouteFlapReversal(
            int previousDistinctRoute, int currentRoute, int nextRoute) {
        boolean previousKnown = previousDistinctRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || previousDistinctRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean currentKnown = currentRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || currentRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        boolean nextKnown = nextRoute == ROUTE_PERFORMANCE_ROUTE_REALTIME
                || nextRoute == ROUTE_PERFORMANCE_ROUTE_FILE;
        return previousKnown && currentKnown && nextKnown
                && previousDistinctRoute != currentRoute
                && nextRoute != currentRoute
                && nextRoute == previousDistinctRoute;
    }

    static String performanceRouteHistoryLabel(int previousDistinctRoute, int currentRoute) {
        String current = performanceRouteLabel(currentRoute);
        if ("-".equals(current)) return current;
        String previous = performanceRouteLabel(previousDistinctRoute);
        if ("-".equals(previous) || previous.equals(current)) return current;
        return previous + ">" + current;
    }

    private static String performanceRouteLabel(int route) {
        if (route == ROUTE_PERFORMANCE_ROUTE_REALTIME) return "rt";
        if (route == ROUTE_PERFORMANCE_ROUTE_FILE) return "file";
        return "-";
    }

    static long routeFlapExtraMarginMs(int routeFlapScore) {
        int safe = Math.max(0, Math.min(ROUTE_FLAP_MAX_SCORE, routeFlapScore));
        return Math.min(ROUTE_FLAP_MAX_EXTRA_MARGIN_MS,
                safe * ROUTE_FLAP_MARGIN_STEP_MS);
    }

    static boolean isTrackableRouteDecisionReason(String reason) {
        return "file-faster".equals(reason)
                || "switch-guard".equals(reason)
                || "flap-damp".equals(reason)
                || "rt-margin".equals(reason)
                || "rt-ready".equals(reason);
    }

    static long adaptiveRouteLatencySwitchGuardMs(
            long realtimeJitterMs, int realtimeSamples,
            long fileJitterMs, int fileSamples) {
        long safeRealtimeJitterMs = Math.max(0L,
                Math.min(MAX_ROUTE_LATENCY_JITTER_MS, realtimeJitterMs));
        long safeFileJitterMs = Math.max(0L,
                Math.min(MAX_ROUTE_LATENCY_JITTER_MS, fileJitterMs));
        long combinedJitterMs = safeRealtimeJitterMs + safeFileJitterMs;
        int minimumSamples = Math.min(
                Math.min(MAX_ROUTE_LATENCY_SAMPLES, Math.max(0, realtimeSamples)),
                Math.min(MAX_ROUTE_LATENCY_SAMPLES, Math.max(0, fileSamples)));

        long guardMs = ROUTE_LATENCY_SWITCH_GUARD_MS;
        if (combinedJitterMs >= ROUTE_PROBE_HIGH_JITTER_MS) {
            guardMs += ROUTE_LATENCY_SWITCH_HIGH_JITTER_EXTRA_MS;
        } else if (combinedJitterMs > ROUTE_PROBE_STABLE_JITTER_MS) {
            guardMs += ROUTE_LATENCY_SWITCH_MODERATE_JITTER_EXTRA_MS;
        }
        if (minimumSamples <= MIN_ROUTE_LATENCY_SAMPLES) {
            guardMs += ROUTE_LATENCY_SWITCH_MIN_SAMPLE_EXTRA_MS;
        }
        if (minimumSamples >= ROUTE_LATENCY_MATURE_SWITCH_SAMPLES
                && combinedJitterMs <= ROUTE_SWITCH_VERY_STABLE_JITTER_MS) {
            guardMs -= ROUTE_LATENCY_SWITCH_STABLE_REDUCTION_MS;
        }
        return Math.max(ROUTE_LATENCY_SWITCH_MIN_GUARD_MS,
                Math.min(ROUTE_LATENCY_SWITCH_MAX_GUARD_MS, guardMs));
    }

    static long adaptiveRouteLatencySwitchExtraMarginMs(
            long realtimeJitterMs, int realtimeSamples,
            long fileJitterMs, int fileSamples) {
        long safeRealtimeJitterMs = Math.max(0L,
                Math.min(MAX_ROUTE_LATENCY_JITTER_MS, realtimeJitterMs));
        long safeFileJitterMs = Math.max(0L,
                Math.min(MAX_ROUTE_LATENCY_JITTER_MS, fileJitterMs));
        long combinedJitterMs = safeRealtimeJitterMs + safeFileJitterMs;
        int minimumSamples = Math.min(
                Math.min(MAX_ROUTE_LATENCY_SAMPLES, Math.max(0, realtimeSamples)),
                Math.min(MAX_ROUTE_LATENCY_SAMPLES, Math.max(0, fileSamples)));

        long extraMarginMs = ROUTE_LATENCY_SWITCH_EXTRA_MARGIN_MS;
        if (minimumSamples <= MIN_ROUTE_LATENCY_SAMPLES
                && combinedJitterMs > ROUTE_PROBE_STABLE_JITTER_MS) {
            extraMarginMs += ROUTE_LATENCY_SWITCH_LOW_CONFIDENCE_EXTRA_MARGIN_MS;
        } else if (minimumSamples >= ROUTE_LATENCY_MATURE_SWITCH_SAMPLES
                && combinedJitterMs <= ROUTE_SWITCH_VERY_STABLE_JITTER_MS) {
            extraMarginMs -= 100L;
        }
        return Math.max(ROUTE_LATENCY_SWITCH_MIN_EXTRA_MARGIN_MS,
                Math.min(ROUTE_LATENCY_SWITCH_MAX_EXTRA_MARGIN_MS, extraMarginMs));
    }

    static long routeLatencySwitchGuardRemainingMs(
            long realtimeJitterMs, int realtimeSamples, long realtimeSampleAtMs,
            long fileJitterMs, int fileSamples, long fileSampleAtMs, long nowMs) {
        if (realtimeJitterMs < 0L || fileJitterMs < 0L
                || realtimeSamples < MIN_ROUTE_LATENCY_SAMPLES
                || fileSamples < MIN_ROUTE_LATENCY_SAMPLES) return 0L;
        if (!isRouteLatencyFresh(fileSampleAtMs, nowMs)
                || fileSampleAtMs <= realtimeSampleAtMs
                || realtimeSampleAtMs <= 0L || nowMs < realtimeSampleAtMs) return 0L;
        long guardMs = adaptiveRouteLatencySwitchGuardMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
        long elapsedMs = nowMs - realtimeSampleAtMs;
        return elapsedMs >= guardMs ? 0L : guardMs - elapsedMs;
    }

    static long routeLatencySwitchMarginMs(
            long realtimeJitterMs, int realtimeSamples, long realtimeSampleAtMs,
            long fileJitterMs, int fileSamples, long fileSampleAtMs, long nowMs) {
        return routeLatencySwitchMarginMs(
                realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileJitterMs, fileSamples, fileSampleAtMs, nowMs, 0);
    }

    static long routeLatencySwitchMarginMs(
            long realtimeJitterMs, int realtimeSamples, long realtimeSampleAtMs,
            long fileJitterMs, int fileSamples, long fileSampleAtMs, long nowMs,
            int routeFlapScore) {
        long baseMarginMs = routeLatencyPreferenceMarginMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
        if (baseMarginMs == Long.MAX_VALUE) return Long.MAX_VALUE;
        baseMarginMs += routeFlapExtraMarginMs(routeFlapScore);
        long switchGuardMs = adaptiveRouteLatencySwitchGuardMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
        boolean newerFileEvidence = fileSampleAtMs > realtimeSampleAtMs;
        boolean recentRealtimeEvidence = realtimeSampleAtMs > 0L
                && nowMs >= realtimeSampleAtMs
                && nowMs - realtimeSampleAtMs < switchGuardMs;
        if (!newerFileEvidence || !recentRealtimeEvidence) return baseMarginMs;
        return baseMarginMs + adaptiveRouteLatencySwitchExtraMarginMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
    }

    static boolean hasConfidentFileLatencyAdvantage(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long fileEstimateMs, long fileJitterMs, int fileSamples) {
        long realtimeRiskMs = riskAdjustedRouteLatency(realtimeEstimateMs, realtimeJitterMs);
        long fileRiskMs = riskAdjustedRouteLatency(fileEstimateMs, fileJitterMs);
        if (realtimeRiskMs < 0L || fileRiskMs < 0L) return false;
        long requiredMarginMs = routeLatencyPreferenceMarginMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
        if (requiredMarginMs == Long.MAX_VALUE) return false;
        return realtimeRiskMs - fileRiskMs >= requiredMarginMs;
    }

    static boolean hasGuardedFileLatencyAdvantage(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        return hasGuardedFileLatencyAdvantage(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs, 0);
    }

    static boolean hasGuardedFileLatencyAdvantage(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs, int routeFlapScore) {
        long realtimeRiskMs = riskAdjustedRouteLatency(realtimeEstimateMs, realtimeJitterMs);
        long fileRiskMs = riskAdjustedRouteLatency(fileEstimateMs, fileJitterMs);
        if (realtimeRiskMs < 0L || fileRiskMs < 0L) return false;
        long requiredMarginMs = routeLatencySwitchMarginMs(
                realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileJitterMs, fileSamples, fileSampleAtMs, nowMs, routeFlapScore);
        if (requiredMarginMs == Long.MAX_VALUE) return false;
        return realtimeRiskMs - fileRiskMs >= requiredMarginMs;
    }

    static long adaptiveRealtimeProbeIntervalMs(
            long realtimeEstimateMs, long realtimeJitterMs,
            long fileEstimateMs, long fileJitterMs) {
        long realtimeRiskMs = riskAdjustedRouteLatency(realtimeEstimateMs, realtimeJitterMs);
        long fileRiskMs = riskAdjustedRouteLatency(fileEstimateMs, fileJitterMs);
        if (realtimeRiskMs < 0L || fileRiskMs < 0L) return 0L;
        long riskGapMs = realtimeRiskMs - fileRiskMs;
        if (riskGapMs < ROUTE_LATENCY_FILE_MARGIN_MS) return 0L;

        long combinedJitterMs = Math.max(0L, realtimeJitterMs)
                + Math.max(0L, fileJitterMs);
        if (combinedJitterMs >= ROUTE_PROBE_HIGH_JITTER_MS
                || riskGapMs < ROUTE_PROBE_EARLY_MARGIN_MS) {
            return ROUTE_PROBE_MIN_MS;
        }
        if (riskGapMs >= ROUTE_PROBE_STRONG_MARGIN_MS
                && combinedJitterMs <= ROUTE_PROBE_STABLE_JITTER_MS) {
            return ROUTE_PROBE_MAX_MS;
        }
        return ROUTE_PROBE_DEFAULT_MS;
    }

    static long realtimeProbeRemainingMs(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        return realtimeProbeRemainingMs(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs, 0);
    }

    static long realtimeProbeRemainingMs(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs, int routeFlapScore) {
        if (realtimeSamples < MIN_ROUTE_LATENCY_SAMPLES
                || fileSamples < MIN_ROUTE_LATENCY_SAMPLES) return 0L;
        if (!isRouteLatencyFresh(fileSampleAtMs, nowMs)
                || realtimeSampleAtMs <= 0L || nowMs < realtimeSampleAtMs) return 0L;
        if (!hasGuardedFileLatencyAdvantage(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs,
                routeFlapScore)) return 0L;
        long intervalMs = adaptiveRealtimeProbeIntervalMs(
                realtimeEstimateMs, realtimeJitterMs, fileEstimateMs, fileJitterMs);
        if (intervalMs <= 0L) return 0L;
        long elapsedMs = nowMs - realtimeSampleAtMs;
        return elapsedMs >= intervalMs ? 0L : intervalMs - elapsedMs;
    }

    static String routeLatencyDecisionReason(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        return routeLatencyDecisionReason(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs, 0);
    }

    static String routeLatencyDecisionReason(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs, int routeFlapScore) {
        long riskGapMs = routeLatencyRiskGapMs(
                realtimeEstimateMs, realtimeJitterMs, fileEstimateMs, fileJitterMs);
        long rawBaseMarginMs = routeLatencyPreferenceMarginMs(
                realtimeJitterMs, realtimeSamples, fileJitterMs, fileSamples);
        if (riskGapMs == Long.MIN_VALUE || rawBaseMarginMs == Long.MAX_VALUE) return "learning";
        if (!isRouteLatencyFresh(fileSampleAtMs, nowMs)) return "file-stale";
        if (realtimeSampleAtMs <= 0L || nowMs < realtimeSampleAtMs) return "rt-clock";

        long flapExtraMarginMs = routeFlapExtraMarginMs(routeFlapScore);
        long effectiveBaseMarginMs = rawBaseMarginMs + flapExtraMarginMs;
        long probeRemainingMs = realtimeProbeRemainingMs(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs,
                routeFlapScore);
        if (probeRemainingMs > 0L) return "file-faster";

        long requiredMarginMs = routeLatencySwitchMarginMs(
                realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileJitterMs, fileSamples, fileSampleAtMs, nowMs, routeFlapScore);
        long guardRemainingMs = routeLatencySwitchGuardRemainingMs(
                realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileJitterMs, fileSamples, fileSampleAtMs, nowMs);
        if (guardRemainingMs > 0L
                && riskGapMs >= effectiveBaseMarginMs
                && riskGapMs < requiredMarginMs) return "switch-guard";
        if (flapExtraMarginMs > 0L
                && riskGapMs >= rawBaseMarginMs
                && riskGapMs < effectiveBaseMarginMs) return "flap-damp";

        long intervalMs = adaptiveRealtimeProbeIntervalMs(
                realtimeEstimateMs, realtimeJitterMs, fileEstimateMs, fileJitterMs);
        long elapsedMs = nowMs - realtimeSampleAtMs;
        if (requiredMarginMs != Long.MAX_VALUE
                && riskGapMs >= requiredMarginMs
                && intervalMs > 0L
                && elapsedMs >= intervalMs) return "rt-probe";
        if (riskGapMs < effectiveBaseMarginMs) return "rt-margin";
        return "rt-ready";
    }

    static boolean shouldPreferFileForLatency(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs) {
        return shouldPreferFileForLatency(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs, 0);
    }

    static boolean shouldPreferFileForLatency(
            long realtimeEstimateMs, long realtimeJitterMs, int realtimeSamples,
            long realtimeSampleAtMs, long fileEstimateMs, long fileJitterMs,
            int fileSamples, long fileSampleAtMs, long nowMs, int routeFlapScore) {
        return realtimeProbeRemainingMs(
                realtimeEstimateMs, realtimeJitterMs, realtimeSamples, realtimeSampleAtMs,
                fileEstimateMs, fileJitterMs, fileSamples, fileSampleAtMs, nowMs,
                routeFlapScore) > 0L;
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

    private static long routeLatencyRegimeMidpoint(long estimateMs, long sampleMs) {
        long safeEstimate = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS, estimateMs));
        long safeSample = Math.max(0L, Math.min(MAX_ROUTE_LATENCY_SAMPLE_MS, sampleMs));
        return (safeEstimate + safeSample + 1L) / 2L;
    }

    private static int clampPenalty(int value, int max) {
        return Math.max(0, Math.min(Math.max(0, max), value));
    }
}
