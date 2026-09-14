package com.livecopilot.micprobe;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.Test;

public class SemanticContextPolicyTest {
    @Test
    public void keepsNewestTurnsWhenBudgetIsTight() {
        Deque<String> turns = new ArrayDeque<>();
        turns.addLast("стар контекст който вече не е важен");
        turns.addLast("средна реплика");
        turns.addLast("най-новият въпрос за доставката");

        String result = SemanticContextPolicy.buildRecent(turns, 58);

        assertTrue(result.contains("най-новият въпрос"));
        assertFalse(result.contains("стар контекст"));
        assertTrue(result.length() <= 58);
    }

    @Test
    public void preservesChronologicalOrderAmongSelectedTurns() {
        Deque<String> turns = new ArrayDeque<>();
        turns.addLast("първо");
        turns.addLast("второ");
        turns.addLast("трето");

        String result = SemanticContextPolicy.buildRecent(turns, 100);

        assertTrue(result.indexOf("първо") < result.indexOf("второ"));
        assertTrue(result.indexOf("второ") < result.indexOf("трето"));
    }

    @Test
    public void latestTurnIsStillBoundedWhenVeryLong() {
        Deque<String> turns = new ArrayDeque<>();
        turns.addLast("това е много дълга последна реплика която трябва да бъде ограничена безопасно");

        String result = SemanticContextPolicy.buildRecent(turns, 30);

        assertTrue(result.length() <= 30);
        assertTrue(result.startsWith("- това е"));
    }
}
