package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

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
    public void connectedSocketIsRoutableOnlyAfterBlockExpires() {
        assertTrue(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 10_000L, 0L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 10_000L, 12_000L));
        assertTrue(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 12_000L, 12_000L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(false, true, 12_000L, 0L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(true, false, 12_000L, 0L));
    }
}
