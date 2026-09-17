package com.livecopilot.micprobe;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsSnapshotDigitBoundaryTest {
    private static final String LABEL_SUMMARY = "rtslow rtfast ffast fslow";
    private static final String OMISSION_PREFIX = "…more+";
    private static final int[] HIDDEN_TOKEN_COUNTS = {
            8, 9, 10, 11,
            98, 99, 100, 101,
            998, 999, 1000, 1001
    };

    @Test
    public void exactSummaryBudgetTracksEveryOmissionDigitBoundary() {
        for (int hiddenTokens : HIDDEN_TOKEN_COUNTS) {
            String source = sourceWithHiddenTokens(hiddenTokens);
            String expected = LABEL_SUMMARY + " " + OMISSION_PREFIX + hiddenTokens;
            int exactBudget = expected.length();

            String rendered = boundedStatsContent(source, exactBudget);
            String context = "hidden=" + hiddenTokens + " budget=" + exactBudget;

            assertEquals(context, expected, rendered);
            assertTrue(context, rendered.length() <= exactBudget);
            assertOmissionCountMatches(source, rendered, context);

            String tighter = boundedStatsContent(source, exactBudget - 1);
            String tighterContext = "hidden=" + hiddenTokens + " budget=" + (exactBudget - 1);
            assertTrue(tighterContext, tighter.length() <= exactBudget - 1);
            assertFalse(tighterContext, expected.equals(tighter));
            assertOmissionCountMatches(source, tighter, tighterContext);
        }
    }

    @Test
    public void markerGrowthAtNineNinetyNineAndNineNinetyNineCostsExactlyOneCharacter() {
        assertBoundaryGrowth(9, 10);
        assertBoundaryGrowth(99, 100);
        assertBoundaryGrowth(999, 1000);
    }

    @Test
    public void everyBoundaryRemainsExactAcrossAllProductionStatsBudgets() {
        int[] boundaryCounts = {9, 10, 99, 100, 999, 1000};
        for (int hiddenTokens : boundaryCounts) {
            String source = sourceWithHiddenTokens(hiddenTokens);
            for (int budget = 34;
                    budget <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET;
                    budget++) {
                String first = boundedStatsContent(source, budget);
                String second = boundedStatsContent(source, budget);
                String context = "hidden=" + hiddenTokens + " budget=" + budget;

                assertEquals(context, first, second);
                assertTrue(context, first.length() <= budget);
                assertOmissionCountMatches(source, first, context);
            }
        }
    }

    @Test
    public void wholeSnapshotReserveAbsorbsEveryMarkerDigitGrowthAndKeepsGuardTail() {
        int[] boundaryCounts = {9, 10, 99, 100, 999, 1000};
        for (int hiddenTokens : boundaryCounts) {
            String source = sourceWithHiddenTokens(hiddenTokens);
            String expectedStats = " • stats{" + LABEL_SUMMARY
                    + " " + OMISSION_PREFIX + hiddenTokens + "}";
            String longLabel = "digit-boundary-" + "x".repeat(420);

            RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                    longLabel, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    true, longLabel, "pending", Integer.MAX_VALUE, Integer.MAX_VALUE,
                    source,
                    longLabel, Integer.MAX_VALUE,
                    Integer.MAX_VALUE, Long.MAX_VALUE,
                    true, Long.MAX_VALUE - 1L, Long.MAX_VALUE - 2L,
                    Long.MAX_VALUE, Long.MAX_VALUE);

            String first = snapshot.format();
            String second = snapshot.format();
            String context = "whole snapshot hidden=" + hiddenTokens;

            assertEquals(context, first, second);
            assertEquals(context, RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET,
                    first.length());
            assertTrue(context, first.contains(expectedStats));
            assertTrue(context + " missing guard tail", first.contains("guard="));
            assertFalse(context + " hard limit fallback cut the snapshot tail", first.endsWith("…"));
        }
    }

    private static void assertBoundaryGrowth(int lowerHidden, int upperHidden) {
        String lower = LABEL_SUMMARY + " " + OMISSION_PREFIX + lowerHidden;
        String upper = LABEL_SUMMARY + " " + OMISSION_PREFIX + upperHidden;
        assertEquals("digit boundary " + lowerHidden + "->" + upperHidden,
                lower.length() + 1, upper.length());

        String lowerRendered = boundedStatsContent(
                sourceWithHiddenTokens(lowerHidden), lower.length());
        String upperRendered = boundedStatsContent(
                sourceWithHiddenTokens(upperHidden), upper.length());

        assertEquals(lower, lowerRendered);
        assertEquals(upper, upperRendered);
    }

    private static String sourceWithHiddenTokens(int hiddenTokens) {
        StringBuilder out = new StringBuilder(LABEL_SUMMARY);
        for (int i = 0; i < hiddenTokens; i++) {
            out.append(" x");
        }
        return out.toString();
    }

    private static String boundedStatsContent(String value, int budget) {
        try {
            Method method = RouteDecisionDiagnosticsSnapshot.class.getDeclaredMethod(
                    "boundedStatsContent", String.class, int.class);
            method.setAccessible(true);
            return (String) method.invoke(null, value, budget);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Unable to invoke boundedStatsContent", error);
        }
    }

    private static void assertOmissionCountMatches(
            String source, String rendered, String context) {
        String[] sourceTokens = tokens(source);
        String[] renderedTokens = tokens(rendered);
        int markerIndex = -1;
        int omitted = -1;
        for (int i = 0; i < renderedTokens.length; i++) {
            if (renderedTokens[i].startsWith(OMISSION_PREFIX)) {
                markerIndex = i;
                omitted = Integer.parseInt(
                        renderedTokens[i].substring(OMISSION_PREFIX.length()));
                break;
            }
        }

        if (markerIndex < 0) {
            assertEquals(context + " token count without omission marker",
                    sourceTokens.length, renderedTokens.length);
            return;
        }

        int visibleSourceTokens = renderedTokens.length - 1;
        assertEquals(context + " omission marker does not match hidden token count",
                sourceTokens.length - visibleSourceTokens, omitted);
    }

    private static String[] tokens(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }
}
