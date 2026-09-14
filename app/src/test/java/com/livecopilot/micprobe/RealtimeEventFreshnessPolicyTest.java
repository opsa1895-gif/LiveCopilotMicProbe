package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeEventFreshnessPolicyTest {
    @Test
    public void currentSpeechEpochIsAccepted() {
        assertTrue(RealtimeEventFreshnessPolicy.shouldAccept(7L, 7L));
    }

    @Test
    public void olderSpeechEpochIsRejected() {
        assertFalse(RealtimeEventFreshnessPolicy.shouldAccept(6L, 7L));
    }

    @Test
    public void invalidEpochIsRejected() {
        assertFalse(RealtimeEventFreshnessPolicy.shouldAccept(0L, 0L));
        assertFalse(RealtimeEventFreshnessPolicy.shouldAccept(-1L, 7L));
    }
}
