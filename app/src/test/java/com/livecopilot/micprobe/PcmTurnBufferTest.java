package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class PcmTurnBufferTest {
    @Test
    public void appendsInOrderBelowLimit() {
        PcmTurnBuffer buffer = new PcmTurnBuffer(8);
        buffer.append(new short[]{1, 2, 3});
        buffer.append(new short[]{4, 5});

        assertEquals(5, buffer.size());
        assertArrayEquals(new short[]{1, 2, 3, 4, 5}, buffer.copy());
    }

    @Test
    public void keepsNewestSamplesWhenCapacityIsExceeded() {
        PcmTurnBuffer buffer = new PcmTurnBuffer(5);
        buffer.append(new short[]{1, 2, 3});
        buffer.append(new short[]{4, 5, 6, 7});

        assertEquals(5, buffer.size());
        assertArrayEquals(new short[]{3, 4, 5, 6, 7}, buffer.copy());
    }

    @Test
    public void oversizedAppendKeepsOnlyItsNewestTail() {
        PcmTurnBuffer buffer = new PcmTurnBuffer(4);
        buffer.append(new short[]{9, 8});
        buffer.append(new short[]{1, 2, 3, 4, 5, 6});

        assertEquals(4, buffer.size());
        assertArrayEquals(new short[]{3, 4, 5, 6}, buffer.copy());
    }

    @Test
    public void copyIsIndependentAndClearResetsSize() {
        PcmTurnBuffer buffer = new PcmTurnBuffer(5);
        buffer.append(new short[]{1, 2, 3});
        short[] copy = buffer.copy();
        copy[0] = 99;

        assertArrayEquals(new short[]{1, 2, 3}, buffer.copy());
        buffer.clear();
        assertEquals(0, buffer.size());
        assertArrayEquals(new short[0], buffer.copy());
    }
}
