package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelfEchoFilterTest {
    @Test
    public void exactSuggestionIsEcho() {
        assertTrue(SelfEchoFilter.matchesAny(
                "Да, точно това имах предвид!",
                "Да, точно това имах предвид!"));
        assertEquals("", SelfEchoFilter.removeEchoPrefix(
                "Да, точно това имах предвид!",
                "Да, точно това имах предвид!"));
    }

    @Test
    public void punctuationAndCaseDoNotMatter() {
        assertTrue(SelfEchoFilter.matchesAny(
                "ДА точно това имах предвид",
                "Да, точно това имах предвид!"));
    }

    @Test
    public void strongPartialRepeatIsEcho() {
        assertTrue(SelfEchoFilter.matchesAny(
                "това е добър въпрос нека го проверим",
                "Честно, това е добър въпрос — нека го проверим заедно."));
    }

    @Test
    public void differentQuestionIsNotEcho() {
        assertFalse(SelfEchoFilter.matchesAny(
                "Добре, а колко струва доставката до София?",
                "Да, цената е тази, която виждаш на екрана."));
    }

    @Test
    public void tinyGenericPhraseIsNotEnough() {
        assertFalse(SelfEchoFilter.matchesAny("добре да", "добре да видим"));
    }

    @Test
    public void repeatedSuggestionPlusNewQuestionKeepsQuestion() {
        String suggestion = "Да, цената е тази, която виждаш на екрана.";
        String heard = "Да, цената е тази която виждаш на екрана, а колко струва доставката?";

        assertFalse(SelfEchoFilter.matchesAny(heard, suggestion));
        assertEquals("а колко струва доставката", SelfEchoFilter.removeEchoPrefix(heard, suggestion));
    }

    @Test
    public void repeatedSuggestionPlusLongNovelTailKeepsTail() {
        String suggestion = "Това е добър въпрос, нека го проверим заедно.";
        String heard = "Това е добър въпрос нека го проверим заедно и после ще покажем другия модел";

        assertFalse(SelfEchoFilter.matchesAny(heard, suggestion));
        assertEquals("и после ще покажем другия модел", SelfEchoFilter.removeEchoPrefix(heard, suggestion));
    }

    @Test
    public void shortFillerAfterSuggestionStillCountsAsEcho() {
        String suggestion = "Да, точно това имах предвид.";
        String heard = "Да точно това имах предвид нали така";

        assertTrue(SelfEchoFilter.matchesAny(heard, suggestion));
        assertEquals("", SelfEchoFilter.removeEchoPrefix(heard, suggestion));
    }
}
