package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VoiceActivityPolicyTest {

    @Test
    public void quietFrameCanContinueExistingSpeechWithoutStrongStart() {
        double noiseFloor = -58.0;
        double frame = -49.0;

        assertFalse(VoiceActivityPolicy.isVoiced(frame, noiseFloor, false, false));
        assertTrue(VoiceActivityPolicy.isVoiced(frame, noiseFloor, true, false));
    }

    @Test
    public void clearSpeechStartsWithTwoFrames() {
        assertEquals(2, VoiceActivityPolicy.startFramesRequired(-42.0, -58.0, false));
        assertTrue(VoiceActivityPolicy.isVoiced(-42.0, -58.0, false, false));
    }

    @Test
    public void sustainedQuietSpeechGetsSoftSixFrameStart() {
        assertEquals(6, VoiceActivityPolicy.startFramesRequired(-52.0, -58.0, false));
        assertTrue(VoiceActivityPolicy.isVoiced(-52.0, -58.0, true, false));
    }

    @Test
    public void backgroundNoiseDoesNotOpenTurn() {
        assertEquals(0, VoiceActivityPolicy.startFramesRequired(-54.0, -58.0, false));
        assertFalse(VoiceActivityPolicy.isVoiced(-54.0, -58.0, false, false));
    }

    @Test
    public void silencedClientNeverCountsAsVoice() {
        assertEquals(0, VoiceActivityPolicy.startFramesRequired(-10.0, -60.0, true));
        assertFalse(VoiceActivityPolicy.isVoiced(-10.0, -60.0, true, true));
    }

    @Test
    public void thresholdsFollowHigherNoiseFloorButStayBounded() {
        double strong = VoiceActivityPolicy.startThresholdDb(-42.0);
        double soft = VoiceActivityPolicy.softStartThresholdDb(-42.0);
        double keep = VoiceActivityPolicy.continueThresholdDb(-42.0);

        assertTrue(strong > soft);
        assertTrue(soft > keep);
        assertTrue(strong <= -29.0);
        assertTrue(soft <= -34.0);
        assertTrue(keep <= -34.0);
    }
}
