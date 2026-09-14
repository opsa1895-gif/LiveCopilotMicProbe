package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdaptiveFileSttDeadlinePolicyTest {
    @Test
    public void startsConservative() {
        assertEquals(8_000L, AdaptiveFileSttDeadlinePolicy.initialDeadlineMs());
    }

    @Test
    public void fastSuccessfulTurnsLowerDeadlineGradually() {
        long next = AdaptiveFileSttDeadlinePolicy.nextDeadlineMs(8_000L, 1_000L, false);
        assertEquals(7_250L, next);
        assertTrue(next >= AdaptiveFileSttDeadlinePolicy.MIN_DEADLINE_MS);
    }

    @Test
    public void slowSuccessfulTurnRaisesDeadlineSmoothly() {
        long next = AdaptiveFileSttDeadlinePolicy.nextDeadlineMs(6_000L, 5_000L, false);
        assertEquals(7_000L, next);
    }

    @Test
    public void timeoutRecoversFasterAndCapsAtMaximum() {
        assertEquals(8_000L,
                AdaptiveFileSttDeadlinePolicy.nextDeadlineMs(6_000L, 0L, true));
        assertEquals(10_000L,
                AdaptiveFileSttDeadlinePolicy.nextDeadlineMs(9_500L, 0L, true));
    }

    @Test
    public void successfulSamplesStayWithinBounds() {
        long low = AdaptiveFileSttDeadlinePolicy.nextDeadlineMs(-1L, 0L, false);
        long high = AdaptiveFileSttDeadlinePolicy.nextDeadlineMs(50_000L, 100_000L, false);
        assertTrue(low >= AdaptiveFileSttDeadlinePolicy.MIN_DEADLINE_MS);
        assertTrue(high <= AdaptiveFileSttDeadlinePolicy.MAX_DEADLINE_MS);
    }
}
