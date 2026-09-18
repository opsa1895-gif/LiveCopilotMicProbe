package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsOmissionCountWidthPlateauTest {
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

    private static final int[] TWO_DIGIT_REPRESENTATIVES = {
            10, 11, 42, 98, 99
    };
    private static final int[] THREE_DIGIT_REPRESENTATIVES = {
            100, 101, 314, 998, 999
    };

    private static final String CORE_PREFIX = "route why=";
    private static final String CORE_SUFFIX = " n=1/2";
    private static final String RELEASE_SECTION = " • release=r:ok";
    private static final String HISTORY_SECTION = " • hist=h";
    private static final String FLAP_SECTION = " • flap×1(+0ms)";
    private static final String LATENCY_SECTION = " • lat guard=1/2ms";

    @Test
    public void everyTwoAndThreeDigitCountKeepsItsWidthPlateauReserve() {
        for (int reasonMask = 1; reasonMask < REASON_COMBINATION_COUNT; reasonMask++) {
            int twoDigitLength = expectedStatsSection(reasonMask, 10).length();
            int threeDigitLength = expectedStatsSection(reasonMask, 100).length();
            String context = "reasons=" + binaryMask(reasonMask, REASON_LABELS.length);

            assertEquals(
                    context + " 99->100 must add exactly one reserve character",
                    twoDigitLength + 1,
                    threeDigitLength);

            for (int hidden = 10; hidden <= 99; hidden++) {
                String actual = overflowStatsSection(reasonMask, hidden);
                String expected = expectedStatsSection(reasonMask, hidden);

                assertEquals(
                        context + " hidden=" + hidden + " two-digit stats identity drifted",
                        expected,
                        actual);
                assertEquals(
                        context + " hidden=" + hidden + " two-digit reserve length drifted",
                        twoDigitLength,
                        actual.length());
            }

            for (int hidden = 100; hidden <= 999; hidden++) {
                String actual = overflowStatsSection(reasonMask, hidden);
                String expected = expectedStatsSection(reasonMask, hidden);

                assertEquals(
                        context + " hidden=" + hidden + " three-digit stats identity drifted",
                        expected,
                        actual);
                assertEquals(
                        context + " hidden=" + hidden + " three-digit reserve length drifted",
                        threeDigitLength,
                        actual.length());
            }
        }
    }

    @Test
    public void representativeCountsShareOneBoundaryWithinEachDigitWidth() {
        for (int sectionMask = 0; sectionMask < SECTION_COMBINATION_COUNT; sectionMask++) {
            for (int reasonMask = 1; reasonMask < REASON_COMBINATION_COUNT; reasonMask++) {
                assertBoundaryPlateau(
                        sectionMask, reasonMask, TWO_DIGIT_REPRESENTATIVES);
                assertBoundaryPlateau(
                        sectionMask, reasonMask, THREE_DIGIT_REPRESENTATIVES);
            }
        }
    }

    @Test
    public void representativeCountsShareOneOverflowCoreBudgetWithinEachDigitWidth() {
        for (int sectionMask = 0; sectionMask < SECTION_COMBINATION_COUNT; sectionMask++) {
            for (int reasonMask = 1; reasonMask < REASON_COMBINATION_COUNT; reasonMask++) {
                assertOverflowPlateau(
                        sectionMask, reasonMask, TWO_DIGIT_REPRESENTATIVES);
                assertOverflowPlateau(
                        sectionMask, reasonMask, THREE_DIGIT_REPRESENTATIVES);
            }
        }
    }

    @Test
    public void crossingFromTwoToThreeDigitsMovesBoundaryAndCoreByExactlyOne() {
        for (int sectionMask = 0; sectionMask < SECTION_COMBINATION_COUNT; sectionMask++) {
            for (int reasonMask = 1; reasonMask < REASON_COMBINATION_COUNT; reasonMask++) {
                int boundary99 = boundaryDecisionLength(sectionMask, reasonMask, 99);
                int boundary100 = boundaryDecisionLength(sectionMask, reasonMask, 100);
                int decisionLength = boundary99 + 12;

                String overflow99 =
                        snapshot(sectionMask, reasonMask, 99, decisionLength).format();
                String overflow100 =
                        snapshot(sectionMask, reasonMask, 100, decisionLength).format();
                String context = context(sectionMask, reasonMask);

                assertEquals(
                        context + " 99->100 boundary shift must be exactly one character",
                        boundary99 - 1,
                        boundary100);
                assertEquals(
                        context + " 99->100 must cost exactly one overflow core character",
                        corePart(overflow99).length() - 1,
                        corePart(overflow100).length());
                assertEquals(
                        context + " release changed across 99->100",
                        releasePart(overflow99),
                        releasePart(overflow100));
                assertEquals(
                        context + " tail changed across 99->100",
                        nonStatsTailAfterStats(overflow99),
                        nonStatsTailAfterStats(overflow100));

                assertStatsIdentity(
                        overflow99, reasonMask, 99, context + " hidden=99");
                assertStatsIdentity(
                        overflow100, reasonMask, 100, context + " hidden=100");
                assertGuardIdentity(
                        overflow99, sectionMask, context + " hidden=99");
                assertGuardIdentity(
                        overflow100, sectionMask, context + " hidden=100");
            }
        }
    }

    private static void assertBoundaryPlateau(
            int sectionMask, int reasonMask, int[] counts) {
        int baselineHidden = counts[0];
        int baselineBoundary =
                boundaryDecisionLength(sectionMask, reasonMask, baselineHidden);
        int baselineStatsLength =
                expectedStatsSection(reasonMask, baselineHidden).length();
        String context = context(sectionMask, reasonMask)
                + " width=" + Integer.toString(baselineHidden).length();

        for (int hidden : counts) {
            int boundary = boundaryDecisionLength(sectionMask, reasonMask, hidden);
            String rendered = snapshot(
                    sectionMask, reasonMask, hidden, boundary).format();

            assertEquals(
                    context + " hidden=" + hidden + " stats length left digit-width plateau",
                    baselineStatsLength,
                    expectedStatsSection(reasonMask, hidden).length());
            assertEquals(
                    context + " hidden=" + hidden + " boundary left digit-width plateau",
                    baselineBoundary,
                    boundary);
            assertEquals(
                    context + " hidden=" + hidden + " exact boundary rendering drifted",
                    expectedNormal(sectionMask, reasonMask, hidden, boundary),
                    rendered);
            assertEquals(
                    context + " hidden=" + hidden + " boundary must consume hard ceiling",
                    RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET,
                    rendered.length());

            assertStatsIdentity(
                    rendered, reasonMask, hidden,
                    context + " hidden=" + hidden);
            assertGuardIdentity(
                    rendered, sectionMask,
                    context + " hidden=" + hidden);
        }
    }

    private static void assertOverflowPlateau(
            int sectionMask, int reasonMask, int[] counts) {
        int baselineHidden = counts[0];
        int decisionLength =
                boundaryDecisionLength(sectionMask, reasonMask, baselineHidden) + 12;
        String baseline =
                snapshot(sectionMask, reasonMask, baselineHidden, decisionLength).format();
        int baselineCoreLength = corePart(baseline).length();
        String baselineRelease = releasePart(baseline);
        String baselineTail = nonStatsTailAfterStats(baseline);
        String context = context(sectionMask, reasonMask)
                + " width=" + Integer.toString(baselineHidden).length();

        for (int hidden : counts) {
            String rendered =
                    snapshot(sectionMask, reasonMask, hidden, decisionLength).format();

            assertEquals(
                    context + " hidden=" + hidden + " overflow rendering drifted",
                    expectedOverflow(sectionMask, reasonMask, hidden, decisionLength),
                    rendered);
            assertEquals(
                    context + " hidden=" + hidden + " core budget left digit-width plateau",
                    baselineCoreLength,
                    corePart(rendered).length());
            assertEquals(
                    context + " hidden=" + hidden + " release changed inside plateau",
                    baselineRelease,
                    releasePart(rendered));
            assertEquals(
                    context + " hidden=" + hidden + " tail changed inside plateau",
                    baselineTail,
                    nonStatsTailAfterStats(rendered));

            assertStatsIdentity(
                    rendered, reasonMask, hidden,
                    context + " hidden=" + hidden);
            assertGuardIdentity(
                    rendered, sectionMask,
                    context + " hidden=" + hidden);
        }
    }

    private static String overflowStatsSection(int reasonMask, int hidden) {
        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                "D".repeat(512), 1, 2,
                false, null, "ok", 0, 1,
                source(reasonMask, hidden),
                null, 0,
                0, 0L,
                false, Long.MIN_VALUE, Long.MAX_VALUE,
                0L, 0L);

        return extractStats(snapshot.format());
    }

    private static RouteDecisionDiagnosticsSnapshot snapshot(
            int sectionMask, int reasonMask, int hidden, int decisionLength) {
        boolean hasRelease = (sectionMask & RELEASE) != 0;
        boolean hasHistory = (sectionMask & HISTORY) != 0;
        boolean hasFlap = (sectionMask & FLAP) != 0;
        boolean hasLatency = (sectionMask & LATENCY) != 0;

        return new RouteDecisionDiagnosticsSnapshot(
                "D".repeat(decisionLength), 1, 2,
                hasRelease, hasRelease ? "r" : null, "ok", 0, 1,
                source(reasonMask, hidden),
                hasHistory ? "h" : null, 0,
                hasFlap ? 1 : 0, 0L,
                hasLatency, Long.MIN_VALUE, Long.MAX_VALUE,
                1L, 2L);
    }

    private static int boundaryDecisionLength(
            int sectionMask, int reasonMask, int hidden) {
        return RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET
                - expectedStatsSection(reasonMask, hidden).length()
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
            int sectionMask, int reasonMask, int hidden, int decisionLength) {
        return rawCore(decisionLength)
                + ((sectionMask & RELEASE) != 0 ? RELEASE_SECTION : "")
                + expectedStatsSection(reasonMask, hidden)
                + ((sectionMask & HISTORY) != 0 ? HISTORY_SECTION : "")
                + ((sectionMask & FLAP) != 0 ? FLAP_SECTION : "")
                + ((sectionMask & LATENCY) != 0 ? LATENCY_SECTION : "");
    }

    private static String expectedOverflow(
            int sectionMask, int reasonMask, int hidden, int decisionLength) {
        String stats = expectedStatsSection(reasonMask, hidden);
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

    private static String source(int reasonMask, int hidden) {
        StringBuilder out = new StringBuilder(reasonLabels(reasonMask));
        for (int i = 0; i < hidden; i++) {
            out.append(" x");
        }
        return out.toString();
    }

    private static String expectedStatsSection(int reasonMask, int hidden) {
        return " • stats{" + reasonLabels(reasonMask) + " …more+" + hidden + "}";
    }

    private static String reasonLabels(int reasonMask) {
        StringBuilder out = new StringBuilder();
        for (int reason = 0; reason < REASON_LABELS.length; reason++) {
            if ((reasonMask & (1 << reason)) == 0) continue;
            if (out.length() > 0) out.append(' ');
            out.append(REASON_LABELS[reason]);
        }
        return out.toString();
    }

    private static void assertStatsIdentity(
            String rendered, int reasonMask, int hidden, String context) {
        String stats = extractStats(rendered);

        assertEquals(
                context + " exact stats section drifted",
                expectedStatsSection(reasonMask, hidden),
                stats);
        for (int reason = 0; reason < REASON_LABELS.length; reason++) {
            boolean expected = (reasonMask & (1 << reason)) != 0;
            if (expected) {
                assertTrue(
                        context + " missing reason " + REASON_LABELS[reason],
                        stats.contains(REASON_LABELS[reason]));
            } else {
                assertFalse(
                        context + " leaked reason " + REASON_LABELS[reason],
                        stats.contains(REASON_LABELS[reason]));
            }
        }
        assertTrue(
                context + " missing exact omission marker",
                stats.contains("…more+" + hidden));
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
