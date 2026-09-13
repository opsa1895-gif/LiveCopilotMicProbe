package com.livecopilot.micprobe;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SttContextWindowTest {
    @Test
    public void keepsOnlyNewestTurns() {
        SttContextWindow window = new SttContextWindow(3, 45_000L);
        window.add("едно", 1_000L);
        window.add("две", 2_000L);
        window.add("три", 3_000L);
        window.add("четири", 4_000L);

        assertEquals(Arrays.asList("две", "три", "четири"), window.snapshot(4_500L));
    }

    @Test
    public void resetsAfterIdleGap() {
        SttContextWindow window = new SttContextWindow(4, 10_000L);
        window.add("преди паузата", 1_000L);

        assertTrue(window.snapshot(11_000L).isEmpty());
    }

    @Test
    public void explicitTopicShiftDropsOldContext() {
        SttContextWindow window = new SttContextWindow(4, 45_000L);
        window.add("говорим за цена", 1_000L);
        window.add("между другото нов въпрос за доставка", 2_000L);

        assertEquals(Arrays.asList("между другото нов въпрос за доставка"), window.snapshot(2_100L));
    }

    @Test
    public void ignoresExactDuplicateTurn() {
        SttContextWindow window = new SttContextWindow(4, 45_000L);
        window.add("Каква е цената?", 1_000L);
        window.add("Каква е цената?", 2_000L);

        assertEquals(1, window.snapshot(2_100L).size());
    }
}
