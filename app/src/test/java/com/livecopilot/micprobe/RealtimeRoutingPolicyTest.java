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
                RealtimeRoutingPolicy.shortenBlockAfterBadFile(10_000L, 25_000L));
        assertEquals(9_000L,
                RealtimeRoutingPolicy.shortenBlockAfterBadFile(10_000L, 9_000L));
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
    public void connectedSocketIsRoutableOnlyAfterBlockExpires() {
        assertTrue(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 10_000L, 0L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 10_000L, 12_000L));
        assertTrue(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 12_000L, 12_000L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(false, true, 12_000L, 0L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(true, false, 12_000L, 0L));
    }
}
