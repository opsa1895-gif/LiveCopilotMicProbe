package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingRegimeFlapReleasePolicyTest {
    private static final long NOW = 100_000L;

    @Test
    public void confirmedRealtimeSlowdownReleasesActiveFlapDamping() {
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 99_000L, NOW));
    }

    @Test
    public void confirmedFileSpeedupReleasesActiveFlapDamping() {
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                1, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_000L,
                1_600L, 100L, 4, 99_500L, NOW));
    }

    @Test
    public void oppositeRegimeDirectionKeepsDamping() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 99_000L, NOW));
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_000L,
                1_600L, 100L, 4, 99_500L, NOW));
    }

    @Test
    public void unconfirmedShiftOrNoActiveDampingDoesNotRelease() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, 1,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 99_000L, NOW));
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                0, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 99_000L, NOW));
    }

    @Test
    public void weakOrStaleFileAdvantageKeepsDamping() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_000L, 100L, 4, 99_500L,
                1_500L, 100L, 4, 99_000L, NOW));
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 79_999L, NOW));
    }

    @Test
    public void noisyEvidenceStillRequiresConfidenceAwareMargin() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                3_000L, 900L, 3, 99_500L,
                2_000L, 900L, 3, 99_000L, NOW));
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                3_500L, 900L, 3, 99_500L,
                2_000L, 900L, 3, 99_000L, NOW));
    }


    @Test
    public void confirmedRealtimeSpeedupReleasesStaleFilePreferenceHistory() {
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_500L, 100L, 4, 99_500L,
                1_700L, 100L, 4, 99_000L, NOW));
    }

    @Test
    public void confirmedFileSlowdownReleasesStaleFilePreferenceHistory() {
        assertTrue(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                1, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_800L, 100L, 4, 99_000L,
                2_300L, 100L, 4, 99_500L, NOW));
    }

    @Test
    public void realtimeFavoringShiftKeepsDampingWhileFileStillWinsBaseline() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_100L, 100L, 4, 99_500L,
                1_200L, 100L, 4, 99_000L, NOW));
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                3, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_100L, 100L, 4, 99_000L,
                1_200L, 100L, 4, 99_500L, NOW));
    }

    @Test
    public void realtimeReleaseStillRequiresUsableConfidenceInputs() {
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_500L, 100L, 2, 99_500L,
                1_700L, 100L, 4, 99_000L, NOW));
        assertFalse(RealtimeRoutingPolicy.shouldReleaseRouteFlapHistoryForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_500L, -1L, 4, 99_500L,
                1_700L, 100L, 4, 99_000L, NOW));
    }
    @Test
    public void releaseReasonNamesRealtimeAndFileRegimeDirections() {
        assertEquals("rt-slowdown", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_500L,
                1_600L, 100L, 4, 99_000L, NOW));
        assertEquals("file-speedup", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_600L, 100L, 4, 99_000L,
                1_600L, 100L, 4, 99_500L, NOW));
        assertEquals("rt-speedup", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_500L, 100L, 4, 99_500L,
                1_700L, 100L, 4, 99_000L, NOW));
        assertEquals("file-slowdown", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                1_800L, 100L, 4, 99_000L,
                2_300L, 100L, 4, 99_500L, NOW));
    }

    @Test
    public void releaseReasonIsHiddenWhenRegimeShiftDoesNotQualify() {
        assertEquals("-", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_000L, 100L, 4, 99_500L,
                1_500L, 100L, 4, 99_000L, NOW));
        assertEquals("-", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_REALTIME,
                -1, RealtimeRoutingPolicy.ROUTE_LATENCY_REGIME_CONFIRM_STREAK,
                2_100L, 100L, 4, 99_500L,
                1_200L, 100L, 4, 99_000L, NOW));
        assertEquals("-", RealtimeRoutingPolicy.routeFlapHistoryReleaseReasonForRegimeChange(
                2, RealtimeRoutingPolicy.ROUTE_PERFORMANCE_ROUTE_FILE,
                -1, 1,
                2_600L, 100L, 4, 99_000L,
                1_600L, 100L, 4, 99_500L, NOW));
    }

}
