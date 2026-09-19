package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsConfirmationCountDigitBoundaryTest {
    private static final int STATS = 1;
    private static final int FLAP = 1 << 1;
    private static final int LATENCY = 1 << 2;
    private static final int EXTRA_COMBINATION_COUNT = 1 << 3;

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
    private static final String CORE_SUFFIX = " n=1/2";
    private static final String STATS_SECTION =
            " • stats{rtslow rtfast ffast fslow}";
    private static final String BREAKDOWN =
            "rtslow rtfast ffast fslow";
    private static final String FLAP_SECTION = " • flap×1(+0ms)";
    private static final String LATENCY_SECTION = " • lat guard=1/2ms";

    @Test
    public void releaseStableStreakDigitGrowthMovesBoundaryByExactlyOne() {
        for (boolean historyPresent : new boolean[]{false, true}) {
            for (int extraMask = 0; extraMask < EXTRA_COMBINATION_COUNT; extraMask++) {
                for (int[] transition : DIGIT_TRANSITIONS) {
                    Scenario lower = releasePendingScenario(
                            extraMask, historyPresent, transition[0], 7);
                    Scenario upper = releasePendingScenario(
                            extraMask, historyPresent, transition[1], 7);
                    assertBoundaryShift(lower, upper, 1,
                            "releaseStable=" + transition[0] + "->" + transition[1]);
                }
            }
        }
    }

    @Test
    public void historyStableStreakDigitGrowthMovesBoundaryByExactlyOne() {
        for (boolean releasePresent : new boolean[]{false, true}) {
            for (int extraMask = 0; extraMask < EXTRA_COMBINATION_COUNT; extraMask++) {
                for (int[] transition : DIGIT_TRANSITIONS) {
                    Scenario lower = historyScenario(
                            extraMask, releasePresent, transition[0], 7);
                    Scenario upper = historyScenario(
                            extraMask, releasePresent, transition[1], 7);
                    assertBoundaryShift(lower, upper, 1,
                            "historyStable=" + transition[0] + "->" + transition[1]);
                }
            }
        }
    }

    @Test
    public void confirmTurnsDigitGrowthMovesSingleRenderedOccurrenceByExactlyOne() {
        for (int extraMask = 0; extraMask < EXTRA_COMBINATION_COUNT; extraMask++) {
            for (int[] transition : DIGIT_TRANSITIONS) {
                Scenario releaseLower = releasePendingScenario(
                        extraMask, false, 1, transition[0]);
                Scenario releaseUpper = releasePendingScenario(
                        extraMask, false, 1, transition[1]);
                assertBoundaryShift(
                        releaseLower, releaseUpper, 1,
                        "release confirm=" + transition[0] + "->" + transition[1]);

                Scenario historyLower = historyScenario(
                        extraMask, false, 1, transition[0]);
                Scenario historyUpper = historyScenario(
                        extraMask, false, 1, transition[1]);
                assertBoundaryShift(
                        historyLower, historyUpper, 1,
                        "history confirm=" + transition[0] + "->" + transition[1]);
            }
        }
    }

    @Test
    public void sharedConfirmTurnsDigitGrowthMovesBoundaryByExactlyTwo() {
        for (int extraMask = 0; extraMask < EXTRA_COMBINATION_COUNT; extraMask++) {
            for (int[] transition : DIGIT_TRANSITIONS) {
                Scenario lower = sharedConfirmScenario(extraMask, transition[0]);
                Scenario upper = sharedConfirmScenario(extraMask, transition[1]);

                assertBoundaryShift(
                        lower, upper, 2,
                        "shared confirm=" + transition[0] + "->" + transition[1]);
            }
        }
    }

    @Test
    public void confirmationDigitGrowthKeepsOverflowBudgetsAndUnrelatedSectionsStable() {
        for (int extraMask = 0; extraMask < EXTRA_COMBINATION_COUNT; extraMask++) {
            for (int[] transition : DIGIT_TRANSITIONS) {
                assertOverflowStability(
                        releasePendingScenario(extraMask, true, transition[0], 7),
                        releasePendingScenario(extraMask, true, transition[1], 7),
                        "releaseStable=" + transition[0] + "->" + transition[1]);

                assertOverflowStability(
                        historyScenario(extraMask, true, transition[0], 7),
                        historyScenario(extraMask, true, transition[1], 7),
                        "historyStable=" + transition[0] + "->" + transition[1]);

                assertOverflowStability(
                        sharedConfirmScenario(extraMask, transition[0]),
                        sharedConfirmScenario(extraMask, transition[1]),
                        "sharedConfirm=" + transition[0] + "->" + transition[1]);
            }
        }
    }

    private static void assertBoundaryShift(
            Scenario lower, Scenario upper, int expectedShift, String label) {
        int lowerBoundary = boundaryDecisionLength(lower);
        int upperBoundary = boundaryDecisionLength(upper);
        String context = lower.context() + " " + label;

        assertEquals(
                context + " raw digit growth moved boundary by wrong amount",
                lowerBoundary - expectedShift,
                upperBoundary);

        String lowerRendered = lower.snapshot(lowerBoundary).format();
        String upperRendered = upper.snapshot(lowerBoundary).format();

        assertEquals(
                context + " lower snapshot must remain normal at shared boundary",
                expectedNormal(lower, lowerBoundary),
                lowerRendered);
        assertEquals(
                context + " upper digit growth alone must switch snapshot to overflow",
                expectedOverflow(upper, lowerBoundary),
                upperRendered);

        assertEquals(
                context + " lower boundary must consume exact snapshot ceiling",
                RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET,
                lowerRendered.length());
        assertTrue(
                context + " overflow exceeded snapshot ceiling",
                upperRendered.length()
                        <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);

        assertTrue(
                context + " normal boundary lost full decision label",
                lowerRendered.contains("D".repeat(lowerBoundary)));
        assertFalse(
                context + " overflow failed to truncate full decision label",
                upperRendered.contains("D".repeat(lowerBoundary)));

        assertProtectedIdentity(lowerRendered, lower, context + " lower");
        assertProtectedIdentity(upperRendered, upper, context + " upper");
    }

    private static void assertOverflowStability(
            Scenario lower, Scenario upper, String label) {
        int decisionLength = boundaryDecisionLength(lower) + 12;
        String lowerRendered = lower.snapshot(decisionLength).format();
        String upperRendered = upper.snapshot(decisionLength).format();
        String context = lower.context() + " overflow " + label;

        assertEquals(
                context + " lower overflow rendering drifted",
                expectedOverflow(lower, decisionLength),
                lowerRendered);
        assertEquals(
                context + " upper overflow rendering drifted",
                expectedOverflow(upper, decisionLength),
                upperRendered);

        assertEquals(
                context + " core budget was redistributed",
                corePart(lowerRendered).length(),
                corePart(upperRendered).length());
        assertEquals(
                context + " stats section changed unexpectedly",
                statsPart(lowerRendered),
                statsPart(upperRendered));
        assertEquals(
                context + " flap/latency tail changed unexpectedly",
                tailAfterHistory(lowerRendered),
                tailAfterHistory(upperRendered));

        assertProtectedIdentity(lowerRendered, lower, context + " lower");
        assertProtectedIdentity(upperRendered, upper, context + " upper");
    }

    private static Scenario releasePendingScenario(
            int extraMask,
            boolean historyPresent,
            int releaseStableStreak,
            int confirmTurns) {
        return new Scenario(
                extraMask,
                true,
                true,
                releaseStableStreak,
                historyPresent,
                historyPresent ? 1 : 0,
                confirmTurns);
    }

    private static Scenario historyScenario(
            int extraMask,
            boolean releasePresent,
            int stableRouteStreak,
            int confirmTurns) {
        return new Scenario(
                extraMask,
                releasePresent,
                false,
                0,
                true,
                stableRouteStreak,
                confirmTurns);
    }

    private static Scenario sharedConfirmScenario(
            int extraMask, int confirmTurns) {
        return new Scenario(
                extraMask,
                true,
                true,
                1,
                true,
                1,
                confirmTurns);
    }

    private static int boundaryDecisionLength(Scenario scenario) {
        return RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET
                - CORE_PREFIX.length()
                - CORE_SUFFIX.length()
                - scenario.releaseSection().length()
                - scenario.historySection().length()
                - scenario.statsSection().length()
                - scenario.flapSection().length()
                - scenario.latencySection().length();
    }

    private static String expectedNormal(Scenario scenario, int decisionLength) {
        return rawCore(decisionLength)
                + scenario.releaseSection()
                + scenario.statsSection()
                + scenario.historySection()
                + scenario.flapSection()
                + scenario.latencySection();
    }

    private static String expectedOverflow(Scenario scenario, int decisionLength) {
        boolean hasStats = scenario.hasStats();
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

        return bounded(rawCore(decisionLength), coreBudget, false)
                + bounded(scenario.releaseSection(), releaseBudget, false)
                + scenario.statsSection()
                + bounded(scenario.historySection(), historyBudget, false)
                + bounded(scenario.flapSection(), flapBudget, false)
                + bounded(scenario.latencySection(), latencyBudget, true);
    }

    private static String rawCore(int decisionLength) {
        return CORE_PREFIX + "D".repeat(decisionLength) + CORE_SUFFIX;
    }

    private static void assertProtectedIdentity(
            String rendered, Scenario scenario, String context) {
        if (scenario.releasePresent) {
            assertTrue(
                    context + " missing release section",
                    rendered.contains(" • release=r:"));
        } else {
            assertFalse(
                    context + " unexpected release section",
                    rendered.contains(" • release="));
        }

        if (scenario.historyPresent) {
            assertTrue(
                    context + " missing history identity",
                    rendered.contains(" • hist=h"));
        } else {
            assertFalse(
                    context + " unexpected history section",
                    rendered.contains(" • hist="));
        }

        if (scenario.hasStats()) {
            assertTrue(
                    context + " missing stats identity",
                    rendered.contains(STATS_SECTION));
        } else {
            assertFalse(
                    context + " unexpected stats section",
                    rendered.contains(" • stats{"));
        }

        if (scenario.hasLatency()) {
            assertTrue(
                    context + " missing latency guard",
                    rendered.contains("guard=1/2ms"));
        } else {
            assertFalse(
                    context + " unexpected latency guard",
                    rendered.contains("guard="));
        }
    }

    private static String corePart(String rendered) {
        int end = rendered.indexOf(" • ");
        return end >= 0 ? rendered.substring(0, end) : rendered;
    }

    private static String statsPart(String rendered) {
        int start = rendered.indexOf(" • stats{");
        if (start < 0) return "";
        int end = rendered.indexOf('}', start);
        assertTrue("unclosed stats section: " + rendered, end >= 0);
        return rendered.substring(start, end + 1);
    }

    private static String tailAfterHistory(String rendered) {
        int history = rendered.indexOf(" • hist=");
        if (history >= 0) {
            int next = rendered.indexOf(" • ", history + 3);
            return next >= 0 ? rendered.substring(next) : "";
        }

        int stats = rendered.indexOf(" • stats{");
        if (stats >= 0) {
            int end = rendered.indexOf('}', stats);
            assertTrue("unclosed stats section: " + rendered, end >= 0);
            return rendered.substring(end + 1);
        }

        int release = rendered.indexOf(" • release=");
        if (release >= 0) {
            int next = rendered.indexOf(" • ", release + 3);
            return next >= 0 ? rendered.substring(next) : "";
        }

        int coreEnd = rendered.indexOf(" • ");
        return coreEnd >= 0 ? rendered.substring(coreEnd) : "";
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

    private static final class Scenario {
        final int extraMask;
        final boolean releasePresent;
        final boolean releasePending;
        final int releaseStableStreak;
        final boolean historyPresent;
        final int stableRouteStreak;
        final int confirmTurns;

        Scenario(
                int extraMask,
                boolean releasePresent,
                boolean releasePending,
                int releaseStableStreak,
                boolean historyPresent,
                int stableRouteStreak,
                int confirmTurns) {
            this.extraMask = extraMask;
            this.releasePresent = releasePresent;
            this.releasePending = releasePending;
            this.releaseStableStreak = releaseStableStreak;
            this.historyPresent = historyPresent;
            this.stableRouteStreak = stableRouteStreak;
            this.confirmTurns = confirmTurns;
        }

        boolean hasStats() {
            return (extraMask & STATS) != 0;
        }

        boolean hasFlap() {
            return (extraMask & FLAP) != 0;
        }

        boolean hasLatency() {
            return (extraMask & LATENCY) != 0;
        }

        String releaseSection() {
            if (!releasePresent) return "";
            String out = " • release=r:" + (releasePending ? "pending" : "ok");
            if (releasePending) {
                out += "×" + releaseStableStreak + "/" + confirmTurns;
            }
            return out;
        }

        String historySection() {
            if (!historyPresent) return "";
            String out = " • hist=h";
            if (stableRouteStreak > 0) {
                out += " stable×" + stableRouteStreak + "/" + confirmTurns;
            }
            return out;
        }

        String statsSection() {
            return hasStats() ? STATS_SECTION : "";
        }

        String flapSection() {
            return hasFlap() ? FLAP_SECTION : "";
        }

        String latencySection() {
            return hasLatency() ? LATENCY_SECTION : "";
        }

        RouteDecisionDiagnosticsSnapshot snapshot(int decisionLength) {
            return new RouteDecisionDiagnosticsSnapshot(
                    "D".repeat(decisionLength),
                    1,
                    2,
                    releasePresent,
                    releasePresent ? "r" : null,
                    releasePending ? "pending" : "ok",
                    releaseStableStreak,
                    confirmTurns,
                    hasStats() ? BREAKDOWN : null,
                    historyPresent ? "h" : null,
                    stableRouteStreak,
                    hasFlap() ? 1 : 0,
                    0L,
                    hasLatency(),
                    Long.MIN_VALUE,
                    Long.MAX_VALUE,
                    1L,
                    2L);
        }

        String context() {
            return "extra=" + binaryMask(extraMask, 3)
                    + " release=" + releasePresent
                    + " pending=" + releasePending
                    + " history=" + historyPresent;
        }
    }

    private static String binaryMask(int mask, int width) {
        String value = Integer.toBinaryString(mask);
        return "0".repeat(Math.max(0, width - value.length())) + value;
    }
}
