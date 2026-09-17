package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RecentConfirmationRateWindowTest {
    @Test
    public void rateIsSuppressedUntilMinimumSamples() {
        RecentConfirmationRateWindow window = new RecentConfirmationRateWindow(8);

        window.recordConfirmed();
        assertEquals(1, window.sampleCount());
        assertEquals(-1, window.confirmationRatePercent(2));

        window.recordReverted();
        assertEquals(2, window.sampleCount());
        assertEquals(50, window.confirmationRatePercent(2));
    }

    @Test
    public void oldestResolutionsAgeOutOfEightSampleWindow() {
        RecentConfirmationRateWindow window = new RecentConfirmationRateWindow(8);
        for (int i = 0; i < 8; i++) window.recordConfirmed();

        assertEquals(8, window.sampleCount());
        assertEquals(100, window.confirmationRatePercent(2));

        for (int i = 0; i < 4; i++) window.recordReverted();
        assertEquals(8, window.sampleCount());
        assertEquals(50, window.confirmationRatePercent(2));

        for (int i = 0; i < 4; i++) window.recordReverted();
        assertEquals(8, window.sampleCount());
        assertEquals(0, window.confirmationRatePercent(2));
    }

    @Test
    public void clearResetsWindowState() {
        RecentConfirmationRateWindow window = new RecentConfirmationRateWindow(8);
        for (int i = 0; i < 5; i++) window.recordConfirmed();
        for (int i = 0; i < 3; i++) window.recordReverted();

        window.clear();

        assertEquals(0, window.sampleCount());
        assertEquals(-1, window.confirmationRatePercent(2));
        window.recordReverted();
        window.recordReverted();
        assertEquals(0, window.confirmationRatePercent(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroCapacityIsRejected() {
        new RecentConfirmationRateWindow(0);
    }
}
