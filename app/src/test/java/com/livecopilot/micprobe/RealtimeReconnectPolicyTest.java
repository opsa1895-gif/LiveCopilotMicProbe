package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RealtimeReconnectPolicyTest {
    @Test
    public void reconnectBackoffIsBounded() {
        assertEquals(1_000L, RealtimeReconnectPolicy.delayMs(0));
        assertEquals(2_000L, RealtimeReconnectPolicy.delayMs(1));
        assertEquals(4_000L, RealtimeReconnectPolicy.delayMs(2));
        assertEquals(8_000L, RealtimeReconnectPolicy.delayMs(3));
        assertEquals(8_000L, RealtimeReconnectPolicy.delayMs(8));
    }

    @Test
    public void negativeAttemptUsesFirstDelay() {
        assertEquals(1_000L, RealtimeReconnectPolicy.delayMs(-5));
    }
}
