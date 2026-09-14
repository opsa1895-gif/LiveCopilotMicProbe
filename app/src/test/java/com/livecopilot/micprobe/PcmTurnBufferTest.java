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
    public void repeatedSmallAppendsWrapWithoutLosingOrder() {
        PcmTurnBuffer buffer = new PcmTurnBuffer(5);
        buffer.append(new short[]{1, 2, 3, 4});
        buffer.append(new short[]{5, 6});
        assertArrayEquals(new short[]{2, 3, 4, 5, 6}, buffer.copy());

        buffer.append(new short[]{7, 8});
        assertEquals(5, buffer.size());
        assertArrayEquals(new short[]{4, 5, 6, 7, 8}, buffer.copy());
    }

    @Test
    public void growthPreservesLogicalOrderAfterWrap() {
        PcmTurnBuffer buffer = new PcmTurnBuffer(20_000);
        short[] first = new short[12_000];
        short[] second = new short[6_000];
        for (int i = 0; i < first.length; i++) first[i] = (short) i;
        for (int i = 0; i < second.length; i++) second[i] = (short) (i + 12_000);

        buffer.append(first);
        buffer.append(second);
        short[] out = buffer.copy();

        assertEquals(18_000, out.length);
        assertEquals(first[0], out[0]);
        assertEquals(first[first.length - 1], out[first.length - 1]);
        assertEquals(second[0], out[first.length]);
        assertEquals(second[second.length - 1], out[out.length - 1]);
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
