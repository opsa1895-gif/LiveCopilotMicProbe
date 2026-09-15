package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeStateFreshnessPolicyTest {
    @Test
    public void onlyStrictlyNewerStateSerialIsAccepted() {
        assertTrue(RealtimeStateFreshnessPolicy.shouldAccept(4L, 3L));
        assertFalse(RealtimeStateFreshnessPolicy.shouldAccept(3L, 3L));
        assertFalse(RealtimeStateFreshnessPolicy.shouldAccept(2L, 3L));
        assertFalse(RealtimeStateFreshnessPolicy.shouldAccept(0L, 3L));
    }
}
