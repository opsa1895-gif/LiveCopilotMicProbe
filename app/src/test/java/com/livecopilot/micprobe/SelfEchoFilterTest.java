package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelfEchoFilterTest {
    @Test
    public void exactSuggestionIsEcho() {
        assertTrue(SelfEchoFilter.matchesAny(
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
}
