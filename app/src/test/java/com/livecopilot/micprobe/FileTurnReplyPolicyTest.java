package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class FileTurnReplyPolicyTest {
    @Test
    public void turnEndMarkerFinalizesOnlyMatchingCurrentTurn() {
        assertTrue(FileTurnReplyPolicy.canFinalize(9L, 9L));
        assertFalse(FileTurnReplyPolicy.canFinalize(8L, 9L));
        assertFalse(FileTurnReplyPolicy.canFinalize(-1L, -1L));
    }

    @Test
    public void semanticFallbackRunsOnlyAfterTurnWithoutMainReply() {
        assertTrue(FileTurnReplyPolicy.shouldUseSemanticFallback(false, "цялата реплика"));
        assertFalse(FileTurnReplyPolicy.shouldUseSemanticFallback(true, "цялата реплика"));
        assertFalse(FileTurnReplyPolicy.shouldUseSemanticFallback(false, "   "));
    }

    @Test
    public void focusAccumulatesAcrossForcedChunks() {
        String first = FileTurnReplyPolicy.appendFocus("", "Какво мислиш за", false);
        String complete = FileTurnReplyPolicy.appendFocus(first, "това предложение?", false);
        assertEquals("Какво мислиш за това предложение?", complete);
    }

    @Test
    public void topicShiftDropsEarlierTurnFocus() {
        String focus = FileTurnReplyPolicy.appendFocus("стар въпрос за цена", "между другото нов въпрос", true);
        assertEquals("между другото нов въпрос", focus);
    }

    @Test
    public void longFocusKeepsBeginningAndLatestTail() {
        StringBuilder middle = new StringBuilder("начало ");
        for (int i = 0; i < 900; i++) middle.append('x');
        middle.append(" край");
        String compact = FileTurnReplyPolicy.appendFocus("", middle.toString(), false);
        assertTrue(compact.length() <= FileTurnReplyPolicy.MAX_FOCUS_CHARS);
        assertTrue(compact.startsWith("начало"));
        assertTrue(compact.endsWith("край"));
    }
}
