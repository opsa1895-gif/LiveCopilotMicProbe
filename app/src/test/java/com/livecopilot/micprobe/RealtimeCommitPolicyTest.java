package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeCommitPolicyTest {
    @Test
    public void partialGetsOneGraceWindowAtSoftDeadline() {
        assertTrue(RealtimeCommitPolicy.grantPartialGrace(false, true));
    }

    @Test
    public void noPartialFallsBackAtSoftDeadline() {
        assertFalse(RealtimeCommitPolicy.grantPartialGrace(false, false));
    }

    @Test
    public void finalDeadlineNeverExtendsAgain() {
        assertFalse(RealtimeCommitPolicy.grantPartialGrace(true, true));
    }
}
