package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RealtimeReconnectPolicyTest {
    @Test
    public void reconnectBackoffCoolsDownPersistentFailures() {
        assertEquals(1_000L, RealtimeReconnectPolicy.delayMs(0));
        assertEquals(2_000L, RealtimeReconnectPolicy.delayMs(1));
        assertEquals(4_000L, RealtimeReconnectPolicy.delayMs(2));
        assertEquals(8_000L, RealtimeReconnectPolicy.delayMs(3));
        assertEquals(16_000L, RealtimeReconnectPolicy.delayMs(4));
        assertEquals(30_000L, RealtimeReconnectPolicy.delayMs(5));
        assertEquals(30_000L, RealtimeReconnectPolicy.delayMs(8));
        assertEquals(30_000L, RealtimeReconnectPolicy.MAX_RETRY_DELAY_MS);
    }

    @Test
    public void negativeAttemptUsesFirstDelay() {
        assertEquals(1_000L, RealtimeReconnectPolicy.delayMs(-5));
    }

    @Test
    public void transientRetriesStayFastBeforeStableReset() {
        assertEquals(15_000L, RealtimeReconnectPolicy.STABLE_RESET_MS);
        assertTrue(RealtimeReconnectPolicy.STABLE_RESET_MS > RealtimeReconnectPolicy.delayMs(3));
    }
}
