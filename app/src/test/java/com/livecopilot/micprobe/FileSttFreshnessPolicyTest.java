package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileSttFreshnessPolicyTest {
    @Test
    public void latestFreshResultCanSurface() {
        assertTrue(FileSttFreshnessPolicy.shouldSurface(4L, 4L, 2_000L, true));
    }

    @Test
    public void olderResultCannotSurfaceAfterNewSpeech() {
        assertFalse(FileSttFreshnessPolicy.shouldSurface(3L, 4L, 1_000L, true));
    }

    @Test
    public void slowResultIsTooOldForLiveOverlay() {
        assertFalse(FileSttFreshnessPolicy.shouldSurface(
                4L, 4L, FileSttFreshnessPolicy.MAX_SURFACE_AGE_MS + 1L, true));
    }

    @Test
    public void oldSessionCannotSurface() {
        assertFalse(FileSttFreshnessPolicy.shouldSurface(4L, 4L, 1_000L, false));
    }
}
