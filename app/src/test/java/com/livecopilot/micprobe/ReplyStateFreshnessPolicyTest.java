package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplyStateFreshnessPolicyTest {
    @Test
    public void currentReplyCanApplyState() {
        assertTrue(ReplyStateFreshnessPolicy.shouldApply(3L, 3L, 8L, 8L, 500L, 12_000L, false));
    }

    @Test
    public void supersededReplyCannotApplyState() {
        assertFalse(ReplyStateFreshnessPolicy.shouldApply(3L, 3L, 7L, 8L, 500L, 12_000L, false));
    }

    @Test
    public void previousSessionCannotApplyState() {
        assertFalse(ReplyStateFreshnessPolicy.shouldApply(2L, 3L, 8L, 8L, 500L, 12_000L, false));
    }

    @Test
    public void expiredOrClosedReplyCannotApplyState() {
        assertFalse(ReplyStateFreshnessPolicy.shouldApply(3L, 3L, 8L, 8L, 12_001L, 12_000L, false));
        assertFalse(ReplyStateFreshnessPolicy.shouldApply(3L, 3L, 8L, 8L, 500L, 12_000L, true));
    }
}
