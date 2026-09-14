package com.livecopilot.micprobe;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SttPromptContextPolicyTest {
    @Test
    public void budgetKeepsNewestTurns() {
        String result = SttPromptContextPolicy.buildRecent(
                Arrays.asList(
                        "стар контекст който не е важен",
                        "по-нов контекст",
                        "последният въпрос е най-важен"),
                50);

        assertFalse(result.contains("стар контекст"));
        assertTrue(result.contains("по-нов контекст"));
        assertTrue(result.contains("последният въпрос"));
        assertTrue(result.indexOf("по-нов") < result.indexOf("последният"));
    }

    @Test
    public void newestLongTurnIsTruncatedInsteadOfDropped() {
        String result = SttPromptContextPolicy.buildRecent(
                Arrays.asList("старо", "това е много дълга последна реплика която продължава"),
                20);

        assertEquals(20, result.length());
        assertTrue(result.endsWith("…"));
        assertFalse(result.contains("старо"));
    }

    @Test
    public void normalizesWhitespace() {
        String result = SttPromptContextPolicy.buildRecent(
                Arrays.asList("едно\n  две", "три"),
                40);

        assertEquals("едно две | три", result);
    }
}
