package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VoiceActivityPolicyTest {

    @Test
    public void quietFrameCanContinueExistingSpeechWithoutStartingNewSpeech() {
        double noiseFloor = -58.0;
        double frame = -49.0;

        assertFalse(VoiceActivityPolicy.isVoiced(frame, noiseFloor, false, false));
        assertTrue(VoiceActivityPolicy.isVoiced(frame, noiseFloor, true, false));
    }

    @Test
    public void clearSpeechStartsNormally() {
        assertTrue(VoiceActivityPolicy.isVoiced(-42.0, -58.0, false, false));
    }

    @Test
    public void backgroundNoiseDoesNotStartSpeech() {
        assertFalse(VoiceActivityPolicy.isVoiced(-50.0, -58.0, false, false));
    }

    @Test
    public void silencedClientNeverCountsAsVoice() {
        assertFalse(VoiceActivityPolicy.isVoiced(-10.0, -60.0, true, true));
    }

    @Test
    public void thresholdsFollowHigherNoiseFloorButStayBounded() {
        double start = VoiceActivityPolicy.startThresholdDb(-42.0);
        double keep = VoiceActivityPolicy.continueThresholdDb(-42.0);

        assertTrue(start > keep);
        assertTrue(start <= -29.0);
        assertTrue(keep <= -33.0);
    }
}
