package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SemanticFastDirectPolicyTest {
    @Test
    public void obviousQuestionsUseFastDirectPath() {
        assertTrue(SemanticReplyFallback.usesFastDirect("а колко струва доставката"));
        assertTrue(SemanticReplyFallback.usesFastDirect("добре а кога започва"));
        assertTrue(SemanticReplyFallback.usesFastDirect("здравей"));
    }

    @Test
    public void ordinaryStatementsKeepSemanticGate() {
        assertFalse(SemanticReplyFallback.usesFastDirect("днес времето е приятно"));
        assertFalse(SemanticReplyFallback.usesFastDirect("това изглежда интересно"));
    }
}
