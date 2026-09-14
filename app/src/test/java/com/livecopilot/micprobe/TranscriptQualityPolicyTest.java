package com.livecopilot.micprobe;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TranscriptQualityPolicyTest {
    @Test
    public void rejectsKnownNoisePlaceholders() {
        assertTrue(TranscriptQualityPolicy.isLowQuality("[Music]"));
        assertTrue(TranscriptQualityPolicy.isLowQuality("(аплодисменти)"));
        assertTrue(TranscriptQualityPolicy.isLowQuality("..."));
        assertTrue(TranscriptQualityPolicy.isLowQuality("ъъъ"));
        assertTrue(TranscriptQualityPolicy.isLowQuality("inaudible"));
    }

    @Test
    public void keepsActualSpeechThatMentionsMusicOrIsShort() {
        assertFalse(TranscriptQualityPolicy.isLowQuality("Харесвам тази музика"));
        assertFalse(TranscriptQualityPolicy.isLowQuality("да"));
        assertFalse(TranscriptQualityPolicy.isLowQuality("а колко струва доставката"));
    }
}
