package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsDynamicStatsReserveBoundaryTest {
    private static final int RELEASE = 1;
    private static final int HISTORY = 1 << 1;
    private static final int FLAP = 1 << 2;
    private static final int LATENCY = 1 << 3;
    private static final int COMBINATION_COUNT = 1 << 4;

    private static final int CORE_BASE_BUDGET = 52;
    private static final int CORE_STATS_DISCOUNT = 4;
    private static final int BASE_STATS_RESERVE = 15;

    private static final String LABEL_SUMMARY = "rtslow rtfast ffast fslow";
    private static final String OMISSION_PREFIX = "…more+";
    private static final String[] REASON_LABELS = {
            "rtslow", "rtfast", "ffast", "fslow"
    };
    private static final int[][] DIGIT_TRANSITIONS = {
            {9, 10},
            {99, 100},
            {999, 1000}
    };

    private static final String CORE_PREFIX = "route why=";
    private static final String CORE_SUFFIX = " n=1/2";
    private static final String RELEASE_SECTION = " • release=r:ok";
    private static final String HISTORY_SECTION = " • hist=h";
    private static final String FLAP_SECTION = " • flap×1(+0ms)";
    private static final String LATENCY_SECTION = " • lat guard=1/2ms";

    @Test
    public void everyDigitGrowthMovesEverySnapshotBoundaryByExactlyOneCharacter() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            for (int[] transition : DIGIT_TRANSITIONS) {
                int lowerHidden = transition[0];
                int upperHidden = transition[1];

                String lowerBreakdown = sourceWithHiddenTokens(lowerHidden);
                String upperBreakdown = sourceWithHiddenTokens(upperHidden);
                String lowerStats = omittedStatsSection(lowerHidden);
                String upperStats = omittedStatsSection(upperHidden);

                int lowerBoundary = boundaryDecisionLength(mask, lowerStats);
                int upperBoundary = boundaryDecisionLength(mask, upperStats);
                String context = "mask=" + binaryMask(mask)
                        + " hidden=" + lowerHidden + "->" + upperHidden;

                assertEquals(
                        context + " marker digit growth must add exactly one reserve character",
                        lowerStats.length() + 1,
                        upperStats.length());
                assertEquals(
                        context + " whole-snapshot boundary must move earlier by one character",
                        lowerBoundary - 1,
                        upperBoundary);

                String lowerAtBoundary =
                        snapshot(mask, lowerBoundary, lowerBreakdown).format();
                String upperAtOwnBoundary =
                        snapshot(mask, upperBoundary, upperBreakdown).format();

                assertEquals(
                        context + " lower exact boundary must stay normal",
                        expectedNormal(mask, lowerBoundary, lowerStats),
                        lowerAtBoundary);
                assertEquals(
                        context + " upper exact boundary must stay normal",
                        expectedNormal(mask, upperBoundary, upperStats),
                        upperAtOwnBoundary);
                assertEquals(RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET,
                        lowerAtBoundary.length());
                assertEquals(RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET,
                        upperAtOwnBoundary.length());

                assertProtectedIdentity(lowerAtBoundary, mask, lowerHidden, context + " lower");
                assertProtectedIdentity(upperAtOwnBoundary, mask, upperHidden, context + " upper");
            }
        }
    }

    @Test
    public void oneExtraMarkerDigitAloneSwitchesTheSameSnapshotFromNormalToOverflow() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            for (int[] transition : DIGIT_TRANSITIONS) {
                int lowerHidden = transition[0];
                int upperHidden = transition[1];

                String lowerBreakdown = sourceWithHiddenTokens(lowerHidden);
                String upperBreakdown = sourceWithHiddenTokens(upperHidden);
                String lowerStats = omittedStatsSection(lowerHidden);
                String upperStats = omittedStatsSection(upperHidden);
                int sharedDecisionLength = boundaryDecisionLength(mask, lowerStats);

                String lower = snapshot(mask, sharedDecisionLength, lowerBreakdown).format();
                String upper = snapshot(mask, sharedDecisionLength, upperBreakdown).format();
                String context = "mask=" + binaryMask(mask)
                        + " sharedDecisionLength=" + sharedDecisionLength
                        + " hidden=" + lowerHidden + "->" + upperHidden;

                assertEquals(
                        context + " lower marker must remain on normal path",
                        expectedNormal(mask, sharedDecisionLength, lowerStats),
                        lower);
                assertEquals(
                        context + " upper marker must switch to bounded overflow",
                        expectedOverflow(mask, sharedDecisionLength, upperStats),
                        upper);

                assertTrue(
                        context + " lower normal path lost full decision label",
                        lower.contains("D".repeat(sharedDecisionLength)));
                assertFalse(
                        context + " upper overflow path failed to truncate decision label",
                        upper.contains("D".repeat(sharedDecisionLength)));

                assertProtectedIdentity(lower, mask, lowerHidden, context + " lower");
                assertProtectedIdentity(upper, mask, upperHidden, context + " upper");
                assertTrue(
                        context + " upper overflow exceeded hard ceiling",
                        upper.length() <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);
            }
        }
    }

    @Test
    public void digitGrowthConsumesOnlyCoreBudgetOnceOverflowIsActive() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            for (int[] transition : DIGIT_TRANSITIONS) {
                int lowerHidden = transition[0];
                int upperHidden = transition[1];
                String lowerStats = omittedStatsSection(lowerHidden);
                String upperStats = omittedStatsSection(upperHidden);

                int decisionLength = boundaryDecisionLength(mask, lowerStats) + 8;
                String lower = snapshot(
                        mask, decisionLength, sourceWithHiddenTokens(lowerHidden)).format();
                String upper = snapshot(
                        mask, decisionLength, sourceWithHiddenTokens(upperHidden)).format();
                String context = "mask=" + binaryMask(mask)
                        + " hidden=" + lowerHidden + "->" + upperHidden;

                String lowerCore = corePart(lower);
                String upperCore = corePart(upper);
                assertEquals(
                        context + " extra marker digit must cost exactly one core character",
                        lowerCore.length() - 1,
                        upperCore.length());

                assertEquals(
                        context + " release/history/flap/latency tail changed",
                        nonStatsTailAfterStats(lower),
                        nonStatsTailAfterStats(upper));

                assertProtectedIdentity(lower, mask, lowerHidden, context + " lower");
                assertProtectedIdentity(upper, mask, upperHidden, context + " upper");
            }
        }
    }

    private static RouteDecisionDiagnosticsSnapshot snapshot(
            int mask, int decisionLength, String breakdown) {
        boolean hasRelease = (mask & RELEASE) != 0;
        boolean hasHistory = (mask & HISTORY) != 0;
        boolean hasFlap = (mask & FLAP) != 0;
        boolean hasLatency = (mask & LATENCY) != 0;

        return new RouteDecisionDiagnosticsSnapshot(
                "D".repeat(decisionLength), 1, 2,
                hasRelease, hasRelease ? "r" : null, "ok", 0, 1,
                breakdown,
                hasHistory ? "h" : null, 0,
                hasFlap ? 1 : 0, 0L,
                hasLatency, Long.MIN_VALUE, Long.MAX_VALUE,
                1L, 2L);
    }

    private static int boundaryDecisionLength(int mask, String omittedStats) {
        return RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET
                - omittedStats.length()
                - rawOptionalSectionsLength(mask)
                - CORE_PREFIX.length()
                - CORE_SUFFIX.length();
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
            int mask, int decisionLength, String omittedStats) {
        return rawCore(decisionLength)
                + ((mask & RELEASE) != 0 ? RELEASE_SECTION : "")
                + omittedStats
                + ((mask & HISTORY) != 0 ? HISTORY_SECTION : "")
                + ((mask & FLAP) != 0 ? FLAP_SECTION : "")
                + ((mask & LATENCY) != 0 ? LATENCY_SECTION : "");
    }

    private static String expectedOverflow(
            int mask, int decisionLength, String omittedStats) {
        int coreBudget = CORE_BASE_BUDGET
                - CORE_STATS_DISCOUNT
                - Math.max(0, omittedStats.length() - BASE_STATS_RESERVE);

        return bounded(rawCore(decisionLength), coreBudget)
                + ((mask & RELEASE) != 0 ? RELEASE_SECTION : "")
                + omittedStats
                + ((mask & HISTORY) != 0 ? HISTORY_SECTION : "")
                + ((mask & FLAP) != 0 ? FLAP_SECTION : "")
                + ((mask & LATENCY) != 0 ? LATENCY_SECTION : "");
    }

    private static String rawCore(int decisionLength) {
        return CORE_PREFIX + "D".repeat(decisionLength) + CORE_SUFFIX;
    }

    private static String sourceWithHiddenTokens(int hiddenTokens) {
        StringBuilder out = new StringBuilder(LABEL_SUMMARY);
        for (int i = 0; i < hiddenTokens; i++) {
            out.append(" x");
        }
        return out.toString();
    }

    private static String omittedStatsSection(int hiddenTokens) {
        return " • stats{" + LABEL_SUMMARY + " " + OMISSION_PREFIX + hiddenTokens + "}";
    }

    private static void assertProtectedIdentity(
            String rendered, int mask, int hiddenTokens, String context) {
        assertTrue(context + " missing stats section", rendered.contains(" • stats{"));
        assertTrue(context + " missing exact omission marker",
                rendered.contains(OMISSION_PREFIX + hiddenTokens));
        for (String reason : REASON_LABELS) {
            assertTrue(context + " missing reason " + reason, rendered.contains(reason));
        }

        if ((mask & LATENCY) != 0) {
            assertTrue(context + " missing guard tail", rendered.contains("guard=1/2ms"));
        } else {
            assertFalse(context + " unexpected guard tail", rendered.contains("guard="));
        }
    }

    private static String corePart(String rendered) {
        int cut = rendered.indexOf(" • ");
        return cut >= 0 ? rendered.substring(0, cut) : rendered;
    }

    private static String nonStatsTailAfterStats(String rendered) {
        int statsStart = rendered.indexOf(" • stats{");
        assertTrue("missing stats section: " + rendered, statsStart >= 0);
        int statsEnd = rendered.indexOf('}', statsStart);
        assertTrue("unclosed stats section: " + rendered, statsEnd >= 0);
        return rendered.substring(statsEnd + 1);
    }

    private static String bounded(String value, int budget) {
        if (value == null || value.isEmpty() || budget <= 0) return "";
        if (value.length() <= budget) return value;
        if (budget == 1) return "…";
        return value.substring(0, budget - 1) + '…';
    }

    private static String binaryMask(int mask) {
        String value = Integer.toBinaryString(mask);
        return "0000".substring(Math.min(4, value.length())) + value;
    }
}
