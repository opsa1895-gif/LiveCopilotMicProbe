package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SemanticSchedulingPolicyTest {
    @Test
    public void obviousQuestionGetsFastGap() {
        assertEquals(900L, SemanticSchedulingPolicy.minGapMs("а колко струва доставката"));
        assertEquals(900L, SemanticSchedulingPolicy.minGapMs("добре а кога започва"));
        assertEquals(900L, SemanticSchedulingPolicy.minGapMs("здравей"));
    }

    @Test
    public void ordinaryStatementKeepsConservativeGap() {
        assertEquals(2500L, SemanticSchedulingPolicy.minGapMs("днес времето е приятно"));
        assertEquals(2500L, SemanticSchedulingPolicy.minGapMs("това изглежда интересно"));
    }
}
