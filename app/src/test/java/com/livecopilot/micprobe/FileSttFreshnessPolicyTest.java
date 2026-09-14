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

    @Test
    public void queueTimeRecheckRejectsItemAfterSerialAdvances() {
        long itemSerial = 7L;
        assertTrue(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial, 300L, true));
        assertFalse(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial + 1L, 301L, true));
    }

    @Test
    public void contextCommitAndSurfaceShareLatestWinsGate() {
        long itemSerial = 9L;
        assertTrue(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial, 250L, true));
        assertTrue(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial, 250L, true));
        assertFalse(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial + 1L, 251L, true));
        assertFalse(FileSttFreshnessPolicy.shouldSurface(itemSerial, itemSerial + 1L, 251L, true));
    }

    @Test
    public void networkRetryGateRejectsSupersededAudio() {
        long itemSerial = 12L;
        assertTrue(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial, 400L, true));
        assertFalse(FileSttFreshnessPolicy.shouldAccept(itemSerial, itemSerial + 1L, 401L, true));
    }

    @Test
    public void sameSpeechTurnChunksShareFreshnessToken() {
        long turnSerial = 21L;
        assertTrue(FileSttFreshnessPolicy.shouldAccept(turnSerial, turnSerial, 500L, true));
        assertTrue(FileSttFreshnessPolicy.shouldAccept(turnSerial, turnSerial, 6_500L, true));
        assertFalse(FileSttFreshnessPolicy.shouldAccept(turnSerial, turnSerial + 1L, 6_501L, true));
    }

    @Test
    public void slowCurrentTurnCanEnrichContextWithoutSurfacingLive() {
        long turnSerial = 22L;
        long ageMs = FileSttFreshnessPolicy.MAX_SURFACE_AGE_MS + 1L;
        assertTrue(FileSttFreshnessPolicy.shouldAccept(turnSerial, turnSerial, ageMs, true));
        assertFalse(FileSttFreshnessPolicy.shouldSurface(turnSerial, turnSerial, ageMs, true));
    }

    @Test
    public void veryOldCurrentTurnStopsProcessing() {
        long turnSerial = 23L;
        assertFalse(FileSttFreshnessPolicy.shouldAccept(
                turnSerial, turnSerial, FileSttFreshnessPolicy.MAX_PROCESS_AGE_MS + 1L, true));
    }
}
