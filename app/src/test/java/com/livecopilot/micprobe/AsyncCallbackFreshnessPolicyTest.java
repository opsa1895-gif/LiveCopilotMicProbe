package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AsyncCallbackFreshnessPolicyTest {
    @Test
    public void currentCallbackIsAccepted() {
        assertTrue(AsyncCallbackFreshnessPolicy.shouldAccept(4L, 4L, 9L, 9L, false));
    }

    @Test
    public void staleSerialIsRejected() {
        assertFalse(AsyncCallbackFreshnessPolicy.shouldAccept(4L, 4L, 8L, 9L, false));
    }

    @Test
    public void oldSessionIsRejected() {
        assertFalse(AsyncCallbackFreshnessPolicy.shouldAccept(3L, 4L, 9L, 9L, false));
    }

    @Test
    public void closedClientAndInvalidTokensAreRejected() {
        assertFalse(AsyncCallbackFreshnessPolicy.shouldAccept(4L, 4L, 9L, 9L, true));
        assertFalse(AsyncCallbackFreshnessPolicy.shouldAccept(0L, 4L, 9L, 9L, false));
        assertFalse(AsyncCallbackFreshnessPolicy.shouldAccept(4L, 4L, 0L, 9L, false));
    }
}
