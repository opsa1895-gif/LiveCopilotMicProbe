package com.livecopilot.micprobe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CrossSourceTranscriptPolicyTest {
    @Test
    public void dropsSameUtteranceAcrossSources() {
        assertEquals("", CrossSourceTranscriptPolicy.novelPart(
                "Колко струва това?", "колко струва това"));
        assertEquals("", CrossSourceTranscriptPolicy.novelPart(
                "А колко струва това с доставка?", "колко струва това с доставка"));
    }

    @Test
    public void preservesOnlyNewExtensionWithOriginalFidelity() {
        assertEquals("с Доставка?", CrossSourceTranscriptPolicy.novelPart(
                "Колко струва това?", "Колко струва това с Доставка?"));
        assertEquals("с доставка?", CrossSourceTranscriptPolicy.novelPart(
                "Искам да знам колко струва", "колко струва с доставка?"));
    }

    @Test
    public void keepsDistinctFollowUp() {
        assertEquals("Кога пристига доставката?", CrossSourceTranscriptPolicy.novelPart(
                "Колко струва това?", "Кога пристига доставката?"));
    }
}
