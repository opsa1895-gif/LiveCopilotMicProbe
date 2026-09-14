package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SemanticEpochPolicyTest {
    @Test
    public void currentEpochRuns() {
        assertTrue(SemanticEpochPolicy.shouldRun(8L, 8L));
    }

    @Test
    public void olderEpochIsRejected() {
        assertFalse(SemanticEpochPolicy.shouldRun(7L, 8L));
    }

    @Test
    public void invalidEpochIsRejected() {
        assertFalse(SemanticEpochPolicy.shouldRun(0L, 8L));
        assertFalse(SemanticEpochPolicy.shouldRun(-1L, -1L));
    }
}
