package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRecoveryPolicyTest {
    @Test
    public void latestFreshRecoveryCanDeliver() {
        assertTrue(RealtimeRecoveryPolicy.shouldDeliver(3L, 3L, 2_000L, true));
    }

    @Test
    public void olderRecoveryCannotOverwriteNewerSpeech() {
        assertFalse(RealtimeRecoveryPolicy.shouldDeliver(2L, 3L, 1_000L, true));
    }

    @Test
    public void staleRecoveryIsDropped() {
        assertFalse(RealtimeRecoveryPolicy.shouldDeliver(
                3L, 3L, RealtimeRecoveryPolicy.MAX_DELIVERY_AGE_MS + 1L, true));
    }

    @Test
    public void oldSessionCannotDeliver() {
        assertFalse(RealtimeRecoveryPolicy.shouldDeliver(3L, 3L, 1_000L, false));
    }
}
