package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsReasonCardinalityBoundaryTest {
    private static final int RELEASE = 1;
    private static final int HISTORY = 1 << 1;
    private static final int FLAP = 1 << 2;
    private static final int LATENCY = 1 << 3;
    private static final int SECTION_COMBINATION_COUNT = 1 << 4;

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

    private static final String[] REASON_LABELS = {
            "rtslow", "rtfast", "ffast", "fslow"
    };
    private static final int REASON_COMBINATION_COUNT = 1 << REASON_LABELS.length;

    private static final String CORE_PREFIX = "route why=";
    private static final String CORE_SUFFIX = " n=1/2";
    private static final String RELEASE_SECTION = " • release=r:ok";
    private static final String HISTORY_SECTION = " • hist=h";
    private static final String FLAP_SECTION = " • flap×1(+0ms)";
    private static final String LATENCY_SECTION = " • lat guard=1/2ms";

    @Test
    public void everyReasonSubsetOwnsItsExactNormalOverflowBoundary() {
        for (int sectionMask = 0; sectionMask < SECTION_COMBINATION_COUNT; sectionMask++) {
            for (int reasonMask = 1; reasonMask < REASON_COMBINATION_COUNT; reasonMask++) {
                String stats = statsSection(reasonMask);
                int boundary = boundaryDecisionLength(sectionMask, reasonMask);
                String atBoundary = snapshot(sectionMask, reasonMask, boundary).format();
                String overBoundary = snapshot(sectionMask, reasonMask, boundary + 1).format();
                String context = context(sectionMask, reasonMask);

                assertEquals(
                        context + " exact boundary must remain normal",
                        expectedNormal(sectionMask, reasonMask, boundary),
                        atBoundary);
                assertEquals(
                        context + " normal boundary must consume the exact hard ceiling",
                        RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET,
                        atBoundary.length());

                assertEquals(
                        context + " +1 character must switch to bounded overflow",
                        expectedOverflow(sectionMask, reasonMask, boundary + 1),
                        overBoundary);
                assertTrue(
                        context + " overflow exceeded hard ceiling",
                        overBoundary.length()
                                <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);

                assertTrue(
                        context + " normal boundary lost the full decision label",
                        atBoundary.contains("D".repeat(boundary)));
                assertFalse(
                        context + " overflow failed to truncate the decision label",
                        overBoundary.contains("D".repeat(boundary + 1)));

                assertEquals(
                        context + " stats reserve must equal the exact rendered stats section",
                        stats,
                        extractStats(atBoundary));
                assertReasonIdentity(atBoundary, reasonMask, context + " normal");
                assertReasonIdentity(overBoundary, reasonMask, context + " overflow");
                assertGuardIdentity(atBoundary, sectionMask, context + " normal");
                assertGuardIdentity(overBoundary, sectionMask, context + " overflow");
            }
        }
    }

    @Test
    public void addingAnyReasonMovesBoundaryByItsExactCanonicalLabelCost() {
        for (int sectionMask = 0; sectionMask < SECTION_COMBINATION_COUNT; sectionMask++) {
            for (int lowerMask = 1; lowerMask < REASON_COMBINATION_COUNT; lowerMask++) {
                for (int reason = 0; reason < REASON_LABELS.length; reason++) {
                    int bit = 1 << reason;
                    if ((lowerMask & bit) != 0) continue;

                    int upperMask = lowerMask | bit;
                    String lowerStats = statsSection(lowerMask);
                    String upperStats = statsSection(upperMask);
                    int expectedGrowth = REASON_LABELS[reason].length() + 1;
                    int lowerBoundary = boundaryDecisionLength(sectionMask, lowerMask);
                    int upperBoundary = boundaryDecisionLength(sectionMask, upperMask);
                    String context = context(sectionMask, lowerMask)
                            + " add=" + REASON_LABELS[reason];

                    assertEquals(
                            context + " stats reserve growth must equal one separator plus label",
                            lowerStats.length() + expectedGrowth,
                            upperStats.length());
                    assertEquals(
                            context + " whole-snapshot boundary must move earlier by reserve growth",
                            lowerBoundary - expectedGrowth,
                            upperBoundary);

                    String lower = snapshot(sectionMask, lowerMask, lowerBoundary).format();
                    String upper = snapshot(sectionMask, upperMask, lowerBoundary).format();

                    assertEquals(
                            context + " lower subset must remain normal at shared boundary",
                            expectedNormal(sectionMask, lowerMask, lowerBoundary),
                            lower);
                    assertEquals(
                            context + " added reason alone must switch shared snapshot to overflow",
                            expectedOverflow(sectionMask, upperMask, lowerBoundary),
                            upper);

                    assertTrue(
                            context + " lower snapshot lost full decision label",
                            lower.contains("D".repeat(lowerBoundary)));
                    assertFalse(
                            context + " upper snapshot did not enter overflow",
                            upper.contains("D".repeat(lowerBoundary)));

                    assertReasonIdentity(lower, lowerMask, context + " lower");
                    assertReasonIdentity(upper, upperMask, context + " upper");
                    assertGuardIdentity(lower, sectionMask, context + " lower");
                    assertGuardIdentity(upper, sectionMask, context + " upper");
                }
            }
        }
    }

    @Test
    public void reserveGrowthBeyondBaseThresholdIsPaidOnlyByCore() {
        for (int sectionMask = 0; sectionMask < SECTION_COMBINATION_COUNT; sectionMask++) {
            for (int lowerMask = 1; lowerMask < REASON_COMBINATION_COUNT; lowerMask++) {
                for (int reason = 0; reason < REASON_LABELS.length; reason++) {
                    int bit = 1 << reason;
                    if ((lowerMask & bit) != 0) continue;

                    int upperMask = lowerMask | bit;
                    String lowerStats = statsSection(lowerMask);
                    String upperStats = statsSection(upperMask);
                    int reserveGrowth = upperStats.length() - lowerStats.length();
                    int decisionLength = boundaryDecisionLength(sectionMask, lowerMask) + 12;

                    String lower = snapshot(sectionMask, lowerMask, decisionLength).format();
                    String upper = snapshot(sectionMask, upperMask, decisionLength).format();
                    String context = context(sectionMask, lowerMask)
                            + " add=" + REASON_LABELS[reason];

                    assertEquals(
                            context + " lower overflow rendering drifted",
                            expectedOverflow(sectionMask, lowerMask, decisionLength),
                            lower);
                    assertEquals(
                            context + " upper overflow rendering drifted",
                            expectedOverflow(sectionMask, upperMask, decisionLength),
                            upper);

                    assertEquals(
                            context + " extra stats reserve must cost only core characters",
                            corePart(lower).length() - reserveGrowth,
                            corePart(upper).length());
                    assertEquals(
                            context + " release section changed while reserve grew",
                            releasePart(lower),
                            releasePart(upper));
                    assertEquals(
                            context + " history/flap/latency tail changed while reserve grew",
                            nonStatsTailAfterStats(lower),
                            nonStatsTailAfterStats(upper));

                    assertReasonIdentity(lower, lowerMask, context + " lower");
                    assertReasonIdentity(upper, upperMask, context + " upper");
                    assertGuardIdentity(lower, sectionMask, context + " lower");
                    assertGuardIdentity(upper, sectionMask, context + " upper");
                }
            }
        }
    }

    @Test
    public void singleReasonReserveStraddlesTheFifteenCharacterBaseThresholdExactly() {
        for (int reason = 0; reason < REASON_LABELS.length; reason++) {
            int reasonMask = 1 << reason;
            String stats = statsSection(reasonMask);
            int expectedReserve = 10 + REASON_LABELS[reason].length();
            int expectedExtraCoreTax = Math.max(0, expectedReserve - BASE_STATS_RESERVE);
            int expectedCoreBudget =
                    CORE_BASE_BUDGET - CORE_STATS_DISCOUNT - expectedExtraCoreTax;
            int decisionLength = boundaryDecisionLength(0, reasonMask) + 8;
            String rendered = snapshot(0, reasonMask, decisionLength).format();
            String context = "reason=" + REASON_LABELS[reason];

            assertEquals(context + " single-reason reserve length drifted",
                    expectedReserve, stats.length());
            assertEquals(context + " base-threshold core tax drifted",
                    expectedCoreBudget, corePart(rendered).length());
            assertEquals(context + " overflow rendering drifted",
                    expectedOverflow(0, reasonMask, decisionLength), rendered);
        }

        assertEquals(15, statsSection(1 << 2).length());
        assertEquals(15, statsSection(1 << 3).length());
        assertEquals(16, statsSection(1).length());
        assertEquals(16, statsSection(1 << 1).length());
    }

    private static RouteDecisionDiagnosticsSnapshot snapshot(
            int sectionMask, int reasonMask, int decisionLength) {
        boolean hasRelease = (sectionMask & RELEASE) != 0;
        boolean hasHistory = (sectionMask & HISTORY) != 0;
        boolean hasFlap = (sectionMask & FLAP) != 0;
        boolean hasLatency = (sectionMask & LATENCY) != 0;

        return new RouteDecisionDiagnosticsSnapshot(
                "D".repeat(decisionLength), 1, 2,
                hasRelease, hasRelease ? "r" : null, "ok", 0, 1,
                reasonBreakdown(reasonMask),
                hasHistory ? "h" : null, 0,
                hasFlap ? 1 : 0, 0L,
                hasLatency, Long.MIN_VALUE, Long.MAX_VALUE,
                1L, 2L);
    }

    private static int boundaryDecisionLength(int sectionMask, int reasonMask) {
        return RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET
                - statsSection(reasonMask).length()
                - rawOptionalSectionsLength(sectionMask)
                - CORE_PREFIX.length()
                - CORE_SUFFIX.length();
    }

    private static int rawOptionalSectionsLength(int sectionMask) {
        int length = 0;
        if ((sectionMask & RELEASE) != 0) length += RELEASE_SECTION.length();
        if ((sectionMask & HISTORY) != 0) length += HISTORY_SECTION.length();
        if ((sectionMask & FLAP) != 0) length += FLAP_SECTION.length();
        if ((sectionMask & LATENCY) != 0) length += LATENCY_SECTION.length();
        return length;
    }

    private static String expectedNormal(
            int sectionMask, int reasonMask, int decisionLength) {
        return rawCore(decisionLength)
                + ((sectionMask & RELEASE) != 0 ? RELEASE_SECTION : "")
                + statsSection(reasonMask)
                + ((sectionMask & HISTORY) != 0 ? HISTORY_SECTION : "")
                + ((sectionMask & FLAP) != 0 ? FLAP_SECTION : "")
                + ((sectionMask & LATENCY) != 0 ? LATENCY_SECTION : "");
    }

    private static String expectedOverflow(
            int sectionMask, int reasonMask, int decisionLength) {
        String stats = statsSection(reasonMask);
        int coreBudget = CORE_BASE_BUDGET
                - CORE_STATS_DISCOUNT
                - Math.max(0, stats.length() - BASE_STATS_RESERVE);
        int releaseBudget = RELEASE_BASE_BUDGET - RELEASE_STATS_DISCOUNT;
        int historyBudget = HISTORY_BASE_BUDGET - HISTORY_STATS_DISCOUNT;
        int flapBudget = FLAP_BASE_BUDGET - FLAP_STATS_DISCOUNT;
        int latencyBudget = LATENCY_BASE_BUDGET - LATENCY_STATS_DISCOUNT;

        return bounded(rawCore(decisionLength), coreBudget, false)
                + bounded((sectionMask & RELEASE) != 0 ? RELEASE_SECTION : "",
                        releaseBudget, false)
                + stats
                + bounded((sectionMask & HISTORY) != 0 ? HISTORY_SECTION : "",
                        historyBudget, false)
                + bounded((sectionMask & FLAP) != 0 ? FLAP_SECTION : "",
                        flapBudget, false)
                + bounded((sectionMask & LATENCY) != 0 ? LATENCY_SECTION : "",
                        latencyBudget, true);
    }

    private static String rawCore(int decisionLength) {
        return CORE_PREFIX + "D".repeat(decisionLength) + CORE_SUFFIX;
    }

    private static String reasonBreakdown(int reasonMask) {
        StringBuilder out = new StringBuilder();
        for (int reason = 0; reason < REASON_LABELS.length; reason++) {
            if ((reasonMask & (1 << reason)) == 0) continue;
            if (out.length() > 0) out.append(' ');
            out.append(REASON_LABELS[reason]);
        }
        return out.toString();
    }

    private static String statsSection(int reasonMask) {
        return " • stats{" + reasonBreakdown(reasonMask) + "}";
    }

    private static void assertReasonIdentity(
            String rendered, int reasonMask, String context) {
        String stats = extractStats(rendered);
        for (int reason = 0; reason < REASON_LABELS.length; reason++) {
            boolean expected = (reasonMask & (1 << reason)) != 0;
            if (expected) {
                assertTrue(context + " missing reason " + REASON_LABELS[reason],
                        stats.contains(REASON_LABELS[reason]));
            } else {
                assertFalse(context + " leaked reason " + REASON_LABELS[reason],
                        stats.contains(REASON_LABELS[reason]));
            }
        }
        assertFalse(context + " unexpected omission marker", stats.contains("…more"));
    }

    private static void assertGuardIdentity(
            String rendered, int sectionMask, String context) {
        if ((sectionMask & LATENCY) != 0) {
            assertTrue(context + " missing guard tail", rendered.contains("guard=1/2ms"));
        } else {
            assertFalse(context + " unexpected guard tail", rendered.contains("guard="));
        }
    }

    private static String extractStats(String rendered) {
        int start = rendered.indexOf(" • stats{");
        assertTrue("missing stats section: " + rendered, start >= 0);
        int end = rendered.indexOf('}', start);
        assertTrue("unclosed stats section: " + rendered, end >= 0);
        return rendered.substring(start, end + 1);
    }

    private static String corePart(String rendered) {
        int cut = rendered.indexOf(" • ");
        return cut >= 0 ? rendered.substring(0, cut) : rendered;
    }

    private static String releasePart(String rendered) {
        int releaseStart = rendered.indexOf(" • release=");
        if (releaseStart < 0) return "";
        int statsStart = rendered.indexOf(" • stats{", releaseStart);
        assertTrue("missing stats after release: " + rendered, statsStart >= 0);
        return rendered.substring(releaseStart, statsStart);
    }

    private static String nonStatsTailAfterStats(String rendered) {
        int statsStart = rendered.indexOf(" • stats{");
        assertTrue("missing stats section: " + rendered, statsStart >= 0);
        int statsEnd = rendered.indexOf('}', statsStart);
        assertTrue("unclosed stats section: " + rendered, statsEnd >= 0);
        return rendered.substring(statsEnd + 1);
    }

    private static String bounded(String value, int budget, boolean preserveTail) {
        if (value == null || value.isEmpty() || budget <= 0) return "";
        if (value.length() <= budget) return value;
        if (budget == 1) return "…";
        if (!preserveTail) {
            return value.substring(0, budget - 1) + '…';
        }

        int headBudget = Math.min(20, budget - 1);
        int tailBudget = budget - headBudget - 1;
        return value.substring(0, headBudget) + '…'
                + value.substring(value.length() - tailBudget);
    }

    private static String context(int sectionMask, int reasonMask) {
        return "sections=" + binaryMask(sectionMask, 4)
                + " reasons=" + binaryMask(reasonMask, REASON_LABELS.length);
    }

    private static String binaryMask(int mask, int width) {
        String value = Integer.toBinaryString(mask);
        return "0".repeat(Math.max(0, width - value.length())) + value;
    }
}
