package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingPolicyTest {
    @Test
    public void quickFailureMustHappenInsideStableWindow() {
        assertTrue(RealtimeRoutingPolicy.isQuickFailure(1_000L, 5_000L, 15_000L));
        assertFalse(RealtimeRoutingPolicy.isQuickFailure(1_000L, 16_000L, 15_000L));
        assertFalse(RealtimeRoutingPolicy.isQuickFailure(0L, 5_000L, 15_000L));
        assertFalse(RealtimeRoutingPolicy.isQuickFailure(5_000L, 4_000L, 15_000L));
    }

    @Test
    public void repeatedUnstableConnectionsIncreaseRoutingBlock() {
        assertEquals(0L, RealtimeRoutingPolicy.blockMsForUnstableStreak(0));
        assertEquals(0L, RealtimeRoutingPolicy.blockMsForUnstableStreak(1));
        assertEquals(3_000L, RealtimeRoutingPolicy.blockMsForUnstableStreak(2));
        assertEquals(8_000L, RealtimeRoutingPolicy.blockMsForUnstableStreak(3));
        assertEquals(15_000L, RealtimeRoutingPolicy.blockMsForUnstableStreak(4));
        assertEquals(15_000L, RealtimeRoutingPolicy.blockMsForUnstableStreak(9));
    }

    @Test
    public void transcriptOutcomesBuildAndRecoverPenalty() {
        int penalty = 0;
        penalty = RealtimeRoutingPolicy.nextOutcomePenalty(penalty, false, false);
        assertEquals(1, penalty);
        penalty = RealtimeRoutingPolicy.nextOutcomePenalty(penalty, true, true);
        assertEquals(2, penalty);
        penalty = RealtimeRoutingPolicy.nextOutcomePenalty(penalty, false, true);
        assertEquals(4, penalty);
        penalty = RealtimeRoutingPolicy.nextOutcomePenalty(penalty, true, false);
        assertEquals(2, penalty);
        penalty = RealtimeRoutingPolicy.nextOutcomePenalty(penalty, true, false);
        assertEquals(0, penalty);
    }

    @Test
    public void latencyPenaltyTracksFastNeutralAndSlowRealtime() {
        assertEquals(1, RealtimeRoutingPolicy.nextLatencyPenalty(2, 1_200L));
        assertEquals(2, RealtimeRoutingPolicy.nextLatencyPenalty(2, 2_500L));
        assertEquals(3, RealtimeRoutingPolicy.nextLatencyPenalty(2, 4_000L));
        assertEquals(4, RealtimeRoutingPolicy.nextLatencyPenalty(2, 6_000L));
        assertEquals(2, RealtimeRoutingPolicy.nextLatencyPenalty(2, -1L));
    }

    @Test
    public void slowLatencyRemainsActionableEvenWhenScoreIsSaturated() {
        assertFalse(RealtimeRoutingPolicy.isSlowRealtimeLatency(-1L));
        assertFalse(RealtimeRoutingPolicy.isSlowRealtimeLatency(3_000L));
        assertTrue(RealtimeRoutingPolicy.isSlowRealtimeLatency(3_001L));
        assertEquals(4, RealtimeRoutingPolicy.nextLatencyPenalty(4, 4_000L));
    }

    @Test
    public void stalePenaltiesDecayWithoutNewBadSignals() {
        assertEquals(4, RealtimeRoutingPolicy.decayedPenalty(4, 14_999L, 4));
        assertEquals(3, RealtimeRoutingPolicy.decayedPenalty(4, 15_000L, 4));
        assertEquals(2, RealtimeRoutingPolicy.decayedPenalty(4, 30_000L, 4));
        assertEquals(0, RealtimeRoutingPolicy.decayedPenalty(4, 60_000L, 4));
    }

    @Test
    public void twoGoodRealtimeTurnsResetRecoveredHealth() {
        int streak = RealtimeRoutingPolicy.nextGoodRealtimeStreak(0, true, false, 2_000L);
        assertEquals(1, streak);
        streak = RealtimeRoutingPolicy.nextGoodRealtimeStreak(streak, true, false, 1_200L);
        assertEquals(2, streak);
        assertTrue(RealtimeRoutingPolicy.shouldResetAfterGoodRealtime(streak));
        assertEquals(0, RealtimeRoutingPolicy.nextGoodRealtimeStreak(streak, true, true, 1_000L));
        assertEquals(0, RealtimeRoutingPolicy.nextGoodRealtimeStreak(streak, true, false, 4_000L));
        assertEquals(0, RealtimeRoutingPolicy.nextGoodRealtimeStreak(streak, false, false, 1_000L));
    }

    @Test
    public void effectiveOutcomePenaltyUsesWorseHealthSignal() {
        assertEquals(3, RealtimeRoutingPolicy.effectiveOutcomePenalty(3, 1));
        assertEquals(4, RealtimeRoutingPolicy.effectiveOutcomePenalty(2, 4));
        assertEquals(0, RealtimeRoutingPolicy.effectiveOutcomePenalty(-1, 0));
    }

    @Test
    public void transcriptOutcomePenaltyUsesBoundedHold() {
        assertEquals(0L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(0));
        assertEquals(2_000L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(1));
        assertEquals(5_000L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(2));
        assertEquals(10_000L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(3));
        assertEquals(15_000L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(4));
        assertEquals(15_000L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(9));
    }

    @Test
    public void badFileOutcomeAcceleratesRealtimeProbe() {
        assertEquals(2, RealtimeRoutingPolicy.penaltyAfterBadFile(3));
        assertEquals(0, RealtimeRoutingPolicy.penaltyAfterBadFile(0));
        assertEquals(11_000L,
                RealtimeRoutingPolicy.shortenOutcomeBlockAfterBadFile(10_000L, 25_000L));
        assertEquals(9_000L,
                RealtimeRoutingPolicy.shortenOutcomeBlockAfterBadFile(10_000L, 9_000L));
    }

    @Test
    public void transportAndOutcomeQuarantinesStayIndependent() {
        long transportUntil = 25_000L;
        long outcomeUntil = 25_000L;
        long shortenedOutcome = RealtimeRoutingPolicy.shortenOutcomeBlockAfterBadFile(
                10_000L, outcomeUntil);

        assertEquals(11_000L, shortenedOutcome);
        assertEquals(25_000L, RealtimeRoutingPolicy.effectiveBlockedUntil(
                transportUntil, shortenedOutcome));
        assertEquals(25_000L, RealtimeRoutingPolicy.effectiveBlockedUntil(
                11_000L, 25_000L));
        assertEquals(0L, RealtimeRoutingPolicy.effectiveBlockedUntil(-1L, 0L));
    }

    @Test
    public void fileOutcomeNeedsUsefulFocusAndCoverage() {
        assertTrue(RealtimeRoutingPolicy.isUsableFileOutcome(true, 4, 1, false));
        assertFalse(RealtimeRoutingPolicy.isUsableFileOutcome(true, 3, 2, false));
        assertFalse(RealtimeRoutingPolicy.isUsableFileOutcome(true, 3, 0, true));
        assertFalse(RealtimeRoutingPolicy.isUsableFileOutcome(false, 3, 0, false));
        assertFalse(RealtimeRoutingPolicy.isUsableFileOutcome(true, 0, 0, false));
    }

    @Test
    public void routeLatencyEstimateUsesBoundedEwma() {
        long estimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(-1L, 0, 2_400L);
        int samples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(0, 2_400L);
        assertEquals(2_400L, estimate);
        assertEquals(1, samples);

        estimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(estimate, samples, 1_600L);
        samples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(samples, 1_600L);
        assertEquals(2_200L, estimate);
        assertEquals(2, samples);
        assertEquals(8, RealtimeRoutingPolicy.nextRouteLatencySampleCount(8, 1_000L));
        assertEquals(2_200L, RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                estimate, samples, -1L));
    }

    @Test
    public void relativeLatencyNeedsEnoughFreshConfidenceAndMeaningfulMargin() {
        long now = 100_000L;
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_500L, 100L, 2, 99_000L, 1_700L, 100L, 3, 99_000L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_399L, 100L, 3, 99_000L, 1_700L, 100L, 3, 99_000L, now));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 3, 99_000L, 1_700L, 100L, 3, 99_000L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 3, 79_999L, 1_700L, 100L, 3, 99_000L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                1_500L, 100L, 3, 99_000L, 1_700L, 100L, 3, 99_000L, now));
    }

    @Test
    public void adaptiveProbeCadenceTracksMarginAndJitter() {
        assertEquals(6_000L, RealtimeRoutingPolicy.adaptiveRealtimeProbeIntervalMs(
                2_400L, 100L, 1_700L, 100L));
        assertEquals(12_000L, RealtimeRoutingPolicy.adaptiveRealtimeProbeIntervalMs(
                3_200L, 400L, 1_700L, 200L));
        assertEquals(20_000L, RealtimeRoutingPolicy.adaptiveRealtimeProbeIntervalMs(
                4_200L, 200L, 1_400L, 100L));
        assertEquals(6_000L, RealtimeRoutingPolicy.adaptiveRealtimeProbeIntervalMs(
                4_200L, 1_000L, 1_400L, 700L));
        assertEquals(0L, RealtimeRoutingPolicy.adaptiveRealtimeProbeIntervalMs(
                2_000L, 100L, 1_700L, 100L));
    }

    @Test
    public void adaptiveProbeReopensRealtimeAtDerivedDeadline() {
        long realtimeSampleAt = 95_000L;
        long fileSampleAt = 99_000L;
        assertEquals(1_000L, RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                2_400L, 100L, 3, realtimeSampleAt,
                1_700L, 100L, 3, fileSampleAt, 100_000L));
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 3, realtimeSampleAt,
                1_700L, 100L, 3, fileSampleAt, 100_000L));
        assertEquals(0L, RealtimeRoutingPolicy.realtimeProbeRemainingMs(
                2_400L, 100L, 3, realtimeSampleAt,
                1_700L, 100L, 3, fileSampleAt, 101_000L));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_400L, 100L, 3, realtimeSampleAt,
                1_700L, 100L, 3, fileSampleAt, 101_000L));
    }

    @Test
    public void jitterTracksRecentAbsoluteDeviationAndIsBounded() {
        long estimate = -1L;
        long jitter = -1L;
        int samples = 0;

        jitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(jitter, estimate, samples, 2_400L);
        estimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(estimate, samples, 2_400L);
        samples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(samples, 2_400L);
        assertEquals(0L, jitter);

        jitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(jitter, estimate, samples, 1_600L);
        estimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(estimate, samples, 1_600L);
        samples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(samples, 1_600L);
        assertEquals(800L, jitter);
        assertEquals(2_200L, estimate);

        jitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(jitter, estimate, samples, 2_600L);
        assertEquals(700L, jitter);
        assertEquals(4_500L, RealtimeRoutingPolicy.nextRouteLatencyJitter(
                5_000L, 1_000L, 3, 20_000L));
    }

    @Test
    public void singleExtremeSpikeIsWinsorizedInsteadOfPoisoningHistory() {
        long estimate = 1_600L;
        long jitter = 200L;
        int samples = 3;

        assertEquals(4_600L, RealtimeRoutingPolicy.boundedRouteLatencySample(
                estimate, samples, 20_000L));
        assertTrue(RealtimeRoutingPolicy.isRouteLatencyOutlier(
                estimate, samples, 20_000L));

        long nextJitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                jitter, estimate, samples, 20_000L);
        long nextEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                estimate, samples, 20_000L);
        assertEquals(2_350L, nextEstimate);
        assertEquals(900L, nextJitter);
        assertEquals(3_250L, RealtimeRoutingPolicy.riskAdjustedRouteLatency(
                nextEstimate, nextJitter));
    }

    @Test
    public void sustainedSlowdownStillMovesEstimatorUpward() {
        long estimate = 1_600L;
        long jitter = 200L;
        int samples = 3;

        for (int i = 0; i < 5; i++) {
            long nextJitter = RealtimeRoutingPolicy.nextRouteLatencyJitter(
                    jitter, estimate, samples, 8_000L);
            long nextEstimate = RealtimeRoutingPolicy.nextRouteLatencyEstimate(
                    estimate, samples, 8_000L);
            samples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(samples, 8_000L);
            estimate = nextEstimate;
            jitter = nextJitter;
        }

        assertTrue(estimate >= 5_000L);
        assertTrue(jitter > 0L);
    }

    @Test
    public void recoveryCanMoveFasterThanUpwardOutlierDrift() {
        assertEquals(4_000L, RealtimeRoutingPolicy.boundedRouteLatencySample(
                8_000L, 5, 1_000L));
        assertEquals(5_000L, RealtimeRoutingPolicy.boundedRouteLatencySample(
                2_000L, 5, 9_000L));
        assertEquals(12_000L, RealtimeRoutingPolicy.boundedRouteLatencySample(
                -1L, 0, Long.MAX_VALUE));
        assertFalse(RealtimeRoutingPolicy.isRouteLatencyOutlier(
                2_000L, 5, 3_000L));
    }

    @Test
    public void riskAdjustedLatencyRejectsFastButSpikyFileLane() {
        long now = 100_000L;
        assertTrue(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_600L, 100L, 3, 99_000L, 1_600L, 100L, 3, 99_000L, now));
        assertFalse(RealtimeRoutingPolicy.shouldPreferFileForLatency(
                2_600L, 100L, 3, 99_000L, 1_600L, 800L, 3, 99_000L, now));
        assertEquals(2_700L, RealtimeRoutingPolicy.riskAdjustedRouteLatency(2_600L, 100L));
        assertEquals(2_400L, RealtimeRoutingPolicy.riskAdjustedRouteLatency(1_600L, 800L));
    }

    @Test
    public void realtimePerformanceSamplesRequireUsefulDirectText() {
        assertTrue(RealtimeRoutingPolicy.isRealtimePerformanceSampleEligible(true, false, 1_500L));
        assertFalse(RealtimeRoutingPolicy.isRealtimePerformanceSampleEligible(false, false, 1_000L));
        assertFalse(RealtimeRoutingPolicy.isRealtimePerformanceSampleEligible(true, true, 1_000L));
        assertFalse(RealtimeRoutingPolicy.isRealtimePerformanceSampleEligible(true, false, -1L));
    }

    @Test
    public void filePerformanceEvidenceRequiresHealthyFileLane() {
        assertTrue(RealtimeRoutingPolicy.isFilePerformanceEligible(true, 0, 0L));
        assertFalse(RealtimeRoutingPolicy.isFilePerformanceEligible(true, 1, 0L));
        assertFalse(RealtimeRoutingPolicy.isFilePerformanceEligible(true, 0, 1L));
        assertFalse(RealtimeRoutingPolicy.isFilePerformanceEligible(false, 0, 0L));
    }

    @Test
    public void connectedSocketIsRoutableOnlyAfterBlockExpires() {
        assertTrue(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 10_000L, 0L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 10_000L, 12_000L));
        assertTrue(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 12_000L, 12_000L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(false, true, 12_000L, 0L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(true, false, 12_000L, 0L));
    }
}
