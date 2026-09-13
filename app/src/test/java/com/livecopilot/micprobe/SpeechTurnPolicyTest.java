package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpeechTurnPolicyTest {
    private static final int SAMPLE_RATE = 16_000;
    private static final int FRAME_SAMPLES = 320;

    @Test
    public void shortTurnEndsAfterAbout460ms() {
        int frames = SpeechTurnPolicy.requiredSilenceFrames(
                SAMPLE_RATE * 2, SAMPLE_RATE, FRAME_SAMPLES);
        assertEquals(23, frames);
        assertTrue(SpeechTurnPolicy.shouldEndTurn(
                23, SAMPLE_RATE * 2, SAMPLE_RATE, FRAME_SAMPLES, SAMPLE_RATE));
    }

    @Test
    public void mediumTurnKeepsSlightlyLongerPause() {
        int frames = SpeechTurnPolicy.requiredSilenceFrames(
                SAMPLE_RATE * 3, SAMPLE_RATE, FRAME_SAMPLES);
        assertEquals(26, frames);
    }

    @Test
    public void longTurnKeeps600msPause() {
        int frames = SpeechTurnPolicy.requiredSilenceFrames(
                SAMPLE_RATE * 5, SAMPLE_RATE, FRAME_SAMPLES);
        assertEquals(30, frames);
    }

    @Test
    public void neverEndsBeforeMinimumSegmentLength() {
        assertFalse(SpeechTurnPolicy.shouldEndTurn(
                100, SAMPLE_RATE / 2, SAMPLE_RATE, FRAME_SAMPLES, SAMPLE_RATE));
    }

    @Test
    public void validLongTurnEndsAtThreshold() {
        assertFalse(SpeechTurnPolicy.shouldEndTurn(
                29, SAMPLE_RATE * 5, SAMPLE_RATE, FRAME_SAMPLES, SAMPLE_RATE));
        assertTrue(SpeechTurnPolicy.shouldEndTurn(
                30, SAMPLE_RATE * 5, SAMPLE_RATE, FRAME_SAMPLES, SAMPLE_RATE));
    }
}
