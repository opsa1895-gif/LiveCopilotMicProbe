package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActionableSpeechPolicyTest {
    @Test
    public void questionWordAfterSingleDiscoursePrefixIsActionable() {
        assertTrue(OpenAiCopilotClient.isActionable("а къде е линкът"));
        assertTrue(OpenAiCopilotClient.isActionable("ами защо така"));
        assertTrue(OpenAiCopilotClient.isActionable("чакай кой го каза"));
    }

    @Test
    public void questionWordAfterMultipleDiscoursePrefixesIsActionable() {
        assertTrue(OpenAiCopilotClient.isActionable("добре а кога започва"));
        assertTrue(OpenAiCopilotClient.isActionable("значи а какво правим сега"));
    }

    @Test
    public void discoursePrefixAloneDoesNotMakeStatementActionable() {
        assertFalse(OpenAiCopilotClient.isActionable("а това е хубав модел"));
        assertFalse(OpenAiCopilotClient.isActionable("добре това звучи нормално"));
        assertFalse(OpenAiCopilotClient.isActionable("ами днес времето е приятно"));
    }

    @Test
    public void explicitQuestionMarkStillWins() {
        assertTrue(OpenAiCopilotClient.isActionable("това ли е твоето?"));
    }
}
