package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsSampleCountDigitBoundaryTest {
    private static final int RELEASE = 1;
    private static final int HISTORY = 1 << 1;
    private static final int FLAP = 1 << 2;
    private static final int LATENCY = 1 << 3;
    private static final int STATS = 1 << 4;
    private static final int COMBINATION_COUNT = 1 << 5;

    private static final int CORE_BASE_BUDGET = 52;
    private static final int RELEASE_BASE_BUDGET = 44;
    private static final int HISTORY_BASE_BUDGET = 28;
    private static final int FLAP_BASE_BUDGET = 20;
    private static final int LATENCY_BASE_BUDGET = 80;

    private static final int CORE_STATS_DISCOUNT = 4;
    private static final int RELEASE_STATS_DISCOUNT = 4;
    private static final int HISTORY_STATS_DISCOUNT = 2;
    private static final int FLAP_STATS_DISCOUNT = 2;
    private static final int LATENCY_STATS_DISCOUNT = 3;
    private static final int BASE_STATS_RESERVE = 15;

    private static final int[][] DIGIT_TRANSITIONS = {
            {9, 10},
            {99, 100},
            {999, 1000}
    };

    private static final String CORE_PREFIX = "route why=";
    private static final String RELEASE_SECTION = " • release=r:ok";
    private static final String HISTORY_SECTION = " • hist=h";
    private static final String FLAP_SECTION = " • flap×1(+0ms)";
    private static final String LATENCY_SECTION = " • lat guard=1/2ms";

    private static final String BREAKDOWN =
            "rtslow rtfast ffast fslow";
    private static final String STATS_SECTION =
            " • stats{rtslow rtfast ffast fslow}";

    @Test
    public void realtimeDigitGrowthMovesEveryBoundaryByExactlyOneCharacter() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            for (int[] transition : DIGIT_TRANSITIONS) {
                assertSingleCounterBoundary(mask, transition[0], transition[1], 7, true);
            }
        }
    }

    @Test
    public void fileDigitGrowthMovesEveryBoundaryByExactlyOneCharacter() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            for (int[] transition : DIGIT_TRANSITIONS) {
                assertSingleCounterBoundary(mask, 7, transition[0], transition[1], false);
            }
        }
    }

    @Test
    public void simultaneousDigitGrowthMovesEveryBoundaryByExactlyTwoCharacters() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            for (int[] transition : DIGIT_TRANSITIONS) {
                int lower = transition[0];
                int upper = transition[1];
                int lowerBoundary = boundaryDecisionLength(mask, lower, lower);
                int upperBoundary = boundaryDecisionLength(mask, upper, upper);
                String context = context(mask)
                        + " both=" + lower + "->" + upper;

                assertEquals(
                        context + " combined digit growth must move boundary by two",
                        lowerBoundary - 2,
                        upperBoundary);

                String lowerRendered =
                        snapshot(mask, lowerBoundary, lower, lower).format();
                String upperRendered =
                        snapshot(mask, lowerBoundary, upper, upper).format();

                assertEquals(
                        context + " lower snapshot must remain normal at shared boundary",
                        expectedNormal(mask, lowerBoundary, lower, lower),
                        lowerRendered);
                assertEquals(
                        context + " upper snapshot must switch to overflow from two new digits",
                        expectedOverflow(mask, lowerBoundary, upper, upper),
                        upperRendered);

                assertEquals(
                        context + " lower boundary must consume exact hard ceiling",
                        RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET,
                        lowerRendered.length());
                assertTrue(
                        context + " upper overflow exceeded hard ceiling",
                        upperRendered.length()
                                <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);

                assertProtectedSections(
                        lowerRendered, mask, context + " lower");
                assertProtectedSections(
                        upperRendered, mask, context + " upper");
            }
        }
    }

    @Test
    public void sampleDigitGrowthDoesNotRedistributeOverflowSectionBudgets() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            for (int[] transition : DIGIT_TRANSITIONS) {
                assertOverflowBudgetStability(
                        mask, transition[0], transition[1], 7, true);
                assertOverflowBudgetStability(
                        mask, 7, transition[0], transition[1], false);
            }
        }
    }

    private static void assertSingleCounterBoundary(
            int mask,
            int lowerRealtime,
            int upperRealtimeOrFile,
            int fixedOther,
            boolean realtimeAxis) {
        int lowerRealtimeValue = realtimeAxis ? lowerRealtime : fixedOther;
        int upperRealtimeValue = realtimeAxis ? upperRealtimeOrFile : fixedOther;
        int lowerFileValue = realtimeAxis ? fixedOther : lowerRealtime;
        int upperFileValue = realtimeAxis ? fixedOther : upperRealtimeOrFile;

        int lowerBoundary =
                boundaryDecisionLength(mask, lowerRealtimeValue, lowerFileValue);
        int upperBoundary =
                boundaryDecisionLength(mask, upperRealtimeValue, upperFileValue);
        String axis = realtimeAxis ? "rt" : "file";
        String context = context(mask)
                + " " + axis + "=" + lowerRealtime + "->" + upperRealtimeOrFile;

        assertEquals(
                context + " one new counter digit must move boundary by exactly one",
                lowerBoundary - 1,
                upperBoundary);

        String lowerRendered = snapshot(
                mask, lowerBoundary, lowerRealtimeValue, lowerFileValue).format();
        String upperRendered = snapshot(
                mask, lowerBoundary, upperRealtimeValue, upperFileValue).format();

        assertEquals(
                context + " lower snapshot must remain normal at shared boundary",
                expectedNormal(
                        mask, lowerBoundary, lowerRealtimeValue, lowerFileValue),
                lowerRendered);
        assertEquals(
                context + " upper counter digit alone must switch snapshot to overflow",
                expectedOverflow(
                        mask, lowerBoundary, upperRealtimeValue, upperFileValue),
                upperRendered);

        assertEquals(
                context + " lower boundary must consume exact hard ceiling",
                RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET,
                lowerRendered.length());
        assertTrue(
                context + " upper overflow exceeded hard ceiling",
                upperRendered.length()
                        <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);

        assertTrue(
                context + " normal snapshot lost exact sample suffix",
                lowerRendered.contains(
                        sampleSuffix(lowerRealtimeValue, lowerFileValue)));
        assertFalse(
                context + " overflow unexpectedly retained full decision reason",
                upperRendered.contains("D".repeat(lowerBoundary)));

        assertProtectedSections(
                lowerRendered, mask, context + " lower");
        assertProtectedSections(
                upperRendered, mask, context + " upper");
    }

    private static void assertOverflowBudgetStability(
            int mask,
            int lowerRealtime,
            int upperRealtimeOrFile,
            int fixedOther,
            boolean realtimeAxis) {
        int lowerRealtimeValue = realtimeAxis ? lowerRealtime : fixedOther;
        int upperRealtimeValue = realtimeAxis ? upperRealtimeOrFile : fixedOther;
        int lowerFileValue = realtimeAxis ? fixedOther : lowerRealtime;
        int upperFileValue = realtimeAxis ? fixedOther : upperRealtimeOrFile;
        int decisionLength =
                boundaryDecisionLength(mask, lowerRealtimeValue, lowerFileValue) + 12;

        String lower = snapshot(
                mask, decisionLength, lowerRealtimeValue, lowerFileValue).format();
        String upper = snapshot(
                mask, decisionLength, upperRealtimeValue, upperFileValue).format();
        String axis = realtimeAxis ? "rt" : "file";
        String context = context(mask)
                + " overflow " + axis + "="
                + lowerRealtime + "->" + upperRealtimeOrFile;

        assertEquals(
                context + " lower overflow rendering drifted",
                expectedOverflow(
                        mask, decisionLength, lowerRealtimeValue, lowerFileValue),
                lower);
        assertEquals(
                context + " upper overflow rendering drifted",
                expectedOverflow(
                        mask, decisionLength, upperRealtimeValue, upperFileValue),
                upper);

        assertEquals(
                context + " core budget length changed after sample digit growth",
                corePart(lower).length(),
                corePart(upper).length());
        assertEquals(
                context + " release changed after sample digit growth",
                releasePart(lower),
                releasePart(upper));
        assertEquals(
                context + " stats changed after sample digit growth",
                statsPart(lower),
                statsPart(upper));
        assertEquals(
                context + " history/flap/latency tail changed after sample digit growth",
                nonStatsTailAfterStatsOrCore(lower),
                nonStatsTailAfterStatsOrCore(upper));

        assertProtectedSections(lower, mask, context + " lower");
        assertProtectedSections(upper, mask, context + " upper");
    }

    private static RouteDecisionDiagnosticsSnapshot snapshot(
            int mask, int decisionLength, int realtimeSamples, int fileSamples) {
        boolean hasRelease = (mask & RELEASE) != 0;
        boolean hasHistory = (mask & HISTORY) != 0;
        boolean hasFlap = (mask & FLAP) != 0;
        boolean hasLatency = (mask & LATENCY) != 0;
        boolean hasStats = (mask & STATS) != 0;

        return new RouteDecisionDiagnosticsSnapshot(
                "D".repeat(decisionLength),
                realtimeSamples,
                fileSamples,
                hasRelease,
                hasRelease ? "r" : null,
                "ok",
                0,
                1,
                hasStats ? BREAKDOWN : null,
                hasHistory ? "h" : null,
                0,
                hasFlap ? 1 : 0,
                0L,
                hasLatency,
                Long.MIN_VALUE,
                Long.MAX_VALUE,
                1L,
                2L);
    }

    private static int boundaryDecisionLength(
            int mask, int realtimeSamples, int fileSamples) {
        return RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET
                - rawOptionalSectionsLength(mask)
                - ((mask & STATS) != 0 ? STATS_SECTION.length() : 0)
                - CORE_PREFIX.length()
                - sampleSuffix(realtimeSamples, fileSamples).length();
    }

    private static int rawOptionalSectionsLength(int mask) {
        int length = 0;
        if ((mask & RELEASE) != 0) length += RELEASE_SECTION.length();
        if ((mask & HISTORY) != 0) length += HISTORY_SECTION.length();
        if ((mask & FLAP) != 0) length += FLAP_SECTION.length();
        if ((mask & LATENCY) != 0) length += LATENCY_SECTION.length();
        return length;
    }

    private static String expectedNormal(
            int mask, int decisionLength, int realtimeSamples, int fileSamples) {
        return rawCore(decisionLength, realtimeSamples, fileSamples)
                + ((mask & RELEASE) != 0 ? RELEASE_SECTION : "")
                + ((mask & STATS) != 0 ? STATS_SECTION : "")
                + ((mask & HISTORY) != 0 ? HISTORY_SECTION : "")
                + ((mask & FLAP) != 0 ? FLAP_SECTION : "")
                + ((mask & LATENCY) != 0 ? LATENCY_SECTION : "");
    }

    private static String expectedOverflow(
            int mask, int decisionLength, int realtimeSamples, int fileSamples) {
        boolean hasStats = (mask & STATS) != 0;
        int coreBudget = CORE_BASE_BUDGET;
        int releaseBudget = RELEASE_BASE_BUDGET;
        int historyBudget = HISTORY_BASE_BUDGET;
        int flapBudget = FLAP_BASE_BUDGET;
        int latencyBudget = LATENCY_BASE_BUDGET;

        if (hasStats) {
            coreBudget -= CORE_STATS_DISCOUNT;
            releaseBudget -= RELEASE_STATS_DISCOUNT;
            historyBudget -= HISTORY_STATS_DISCOUNT;
            flapBudget -= FLAP_STATS_DISCOUNT;
            latencyBudget -= LATENCY_STATS_DISCOUNT;
            coreBudget -= Math.max(
                    0, STATS_SECTION.length() - BASE_STATS_RESERVE);
        }

        return bounded(
                        rawCore(decisionLength, realtimeSamples, fileSamples),
                        coreBudget,
                        false)
                + bounded(
                        (mask & RELEASE) != 0 ? RELEASE_SECTION : "",
                        releaseBudget,
                        false)
                + (hasStats ? STATS_SECTION : "")
                + bounded(
                        (mask & HISTORY) != 0 ? HISTORY_SECTION : "",
                        historyBudget,
                        false)
                + bounded(
                        (mask & FLAP) != 0 ? FLAP_SECTION : "",
                        flapBudget,
                        false)
                + bounded(
                        (mask & LATENCY) != 0 ? LATENCY_SECTION : "",
                        latencyBudget,
                        true);
    }

    private static String rawCore(
            int decisionLength, int realtimeSamples, int fileSamples) {
        return CORE_PREFIX
                + "D".repeat(decisionLength)
                + sampleSuffix(realtimeSamples, fileSamples);
    }

    private static String sampleSuffix(int realtimeSamples, int fileSamples) {
        return " n=" + realtimeSamples + "/" + fileSamples;
    }

    private static void assertProtectedSections(
            String rendered, int mask, String context) {
        if ((mask & STATS) != 0) {
            assertTrue(
                    context + " missing exact stats identity",
                    rendered.contains(STATS_SECTION));
        } else {
            assertFalse(
                    context + " unexpected stats section",
                    rendered.contains(" • stats{"));
        }

        if ((mask & LATENCY) != 0) {
            assertTrue(
                    context + " missing guard tail",
                    rendered.contains("guard=1/2ms"));
        } else {
            assertFalse(
                    context + " unexpected guard tail",
                    rendered.contains("guard="));
        }
    }

    private static String corePart(String rendered) {
        int cut = rendered.indexOf(" • ");
        return cut >= 0 ? rendered.substring(0, cut) : rendered;
    }

    private static String releasePart(String rendered) {
        int start = rendered.indexOf(" • release=");
        if (start < 0) return "";
        int end = nextSectionStart(rendered, start + 1);
        return rendered.substring(start, end >= 0 ? end : rendered.length());
    }

    private static String statsPart(String rendered) {
        int start = rendered.indexOf(" • stats{");
        if (start < 0) return "";
        int end = rendered.indexOf('}', start);
        assertTrue("unclosed stats section: " + rendered, end >= 0);
        return rendered.substring(start, end + 1);
    }

    private static String nonStatsTailAfterStatsOrCore(String rendered) {
        int statsStart = rendered.indexOf(" • stats{");
        if (statsStart >= 0) {
            int statsEnd = rendered.indexOf('}', statsStart);
            assertTrue("unclosed stats section: " + rendered, statsEnd >= 0);
            return rendered.substring(statsEnd + 1);
        }

        int coreEnd = rendered.indexOf(" • ");
        return coreEnd >= 0 ? rendered.substring(coreEnd) : "";
    }

    private static int nextSectionStart(String rendered, int fromIndex) {
        return rendered.indexOf(" • ", fromIndex);
    }

    private static String bounded(
            String value, int budget, boolean preserveTail) {
        if (value == null || value.isEmpty() || budget <= 0) return "";
        if (value.length() <= budget) return value;
        if (budget == 1) return "…";
        if (!preserveTail) {
            return value.substring(0, budget - 1) + '…';
        }

        int headBudget = Math.min(20, budget - 1);
        int tailBudget = budget - headBudget - 1;
        return value.substring(0, headBudget)
                + '…'
                + value.substring(value.length() - tailBudget);
    }

    private static String context(int mask) {
        String value = Integer.toBinaryString(mask);
        return "mask=" + "00000".substring(Math.min(5, value.length())) + value;
    }
}
