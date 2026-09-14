package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpeechTurnPolicyTest {
    private static final int SAMPLE_RATE = 16_000;
    private static final int FRAME_SAMPLES = 320;
    private static final int MIN_SEGMENT = SAMPLE_RATE * 3 / 4;

    @Test
    public void veryShortSpeechUsesFast360msEndpoint() {
        assertEquals(18, SpeechTurnPolicy.requiredSilenceFrames(20, SAMPLE_RATE, FRAME_SAMPLES));
    }

    @Test
    public void shortSpeechUses420msEndpoint() {
        assertEquals(21, SpeechTurnPolicy.requiredSilenceFrames(75, SAMPLE_RATE, FRAME_SAMPLES));
    }

    @Test
    public void mediumSpeechKeeps500msPause() {
        assertEquals(25, SpeechTurnPolicy.requiredSilenceFrames(150, SAMPLE_RATE, FRAME_SAMPLES));
    }

    @Test
    public void longSpeechKeeps580msPause() {
        assertEquals(29, SpeechTurnPolicy.requiredSilenceFrames(250, SAMPLE_RATE, FRAME_SAMPLES));
    }

    @Test
    public void shortQuestionCanEndBeforeOneSecondTotalSegment() {
        assertFalse(SpeechTurnPolicy.shouldEndTurn(17, 13_000, 20, SAMPLE_RATE, FRAME_SAMPLES, MIN_SEGMENT));
        assertTrue(SpeechTurnPolicy.shouldEndTurn(18, 13_000, 20, SAMPLE_RATE, FRAME_SAMPLES, MIN_SEGMENT));
    }

    @Test
    public void neverEndsBeforeMinimumSegmentLength() {
        assertFalse(SpeechTurnPolicy.shouldEndTurn(100, SAMPLE_RATE / 2, 30, SAMPLE_RATE, FRAME_SAMPLES, MIN_SEGMENT));
    }

    @Test
    public void noiseBurstWithoutEnoughVoicedFramesDoesNotEndTurn() {
        assertFalse(SpeechTurnPolicy.shouldEndTurn(100, SAMPLE_RATE, 3, SAMPLE_RATE, FRAME_SAMPLES, MIN_SEGMENT));
    }
}
