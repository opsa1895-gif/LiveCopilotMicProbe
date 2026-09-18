package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsOmissionMarkerActivationTest {
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
    private static final String MARKER_ONE_SUFFIX = " …more+1";

    @Test
    public void firstHiddenTokenAddsExactlyTheMarkerAndMovesEveryBoundaryByEightCharacters() {
        for (int sectionMask = 0; sectionMask < SECTION_COMBINATION_COUNT; sectionMask++) {
            for (int reasonMask = 1; reasonMask < REASON_COMBINATION_COUNT; reasonMask++) {
                String withoutMarkerStats = statsSection(reasonMask, 0);
                String withMarkerStats = statsSection(reasonMask, 1);
                int withoutMarkerBoundary = boundaryDecisionLength(sectionMask, reasonMask, 0);
                int withMarkerBoundary = boundaryDecisionLength(sectionMask, reasonMask, 1);
                String context = context(sectionMask, reasonMask);

                assertEquals(
                        context + " first omission marker must be appended atomically",
                        withoutMarkerStats.substring(0, withoutMarkerStats.length() - 1)
                                + MARKER_ONE_SUFFIX + "}",
                        withMarkerStats);
                assertEquals(
                        context + " marker birth must add exactly eight reserve characters",
                        withoutMarkerStats.length() + MARKER_ONE_SUFFIX.length(),
                        withMarkerStats.length());
                assertEquals(
                        context + " whole-snapshot boundary must move earlier by eight characters",
                        withoutMarkerBoundary - MARKER_ONE_SUFFIX.length(),
                        withMarkerBoundary);

                String lower = snapshot(
                        sectionMask, reasonMask, 0, withoutMarkerBoundary).format();
                String upper = snapshot(
                        sectionMask, reasonMask, 1, withoutMarkerBoundary).format();

                assertEquals(
                        context + " zero-hidden snapshot must remain normal at shared boundary",
                        expectedNormal(sectionMask, reasonMask, 0, withoutMarkerBoundary),
                        lower);
                assertEquals(
                        context + " first hidden token alone must switch snapshot to overflow",
                        expectedOverflow(sectionMask, reasonMask, 1, withoutMarkerBoundary),
                        upper);

                assertTrue(
                        context + " zero-hidden normal path lost full decision label",
                        lower.contains("D".repeat(withoutMarkerBoundary)));
                assertFalse(
                        context + " first hidden token failed to enter overflow",
                        upper.contains("D".repeat(withoutMarkerBoundary)));

                assertStatsIdentity(lower, reasonMask, 0, context + " lower");
                assertStatsIdentity(upper, reasonMask, 1, context + " upper");
                assertGuardIdentity(lower, sectionMask, context + " lower");
                assertGuardIdentity(upper, sectionMask, context + " upper");
            }
        }
    }

    @Test
    public void everySingleDigitOmissionCountSharesTheSameBoundaryAndReserveLength() {
        for (int sectionMask = 0; sectionMask < SECTION_COMBINATION_COUNT; sectionMask++) {
            for (int reasonMask = 1; reasonMask < REASON_COMBINATION_COUNT; reasonMask++) {
                int expectedBoundary = boundaryDecisionLength(sectionMask, reasonMask, 1);
                int expectedStatsLength = statsSection(reasonMask, 1).length();
                String context = context(sectionMask, reasonMask);

                for (int hidden = 1; hidden <= 9; hidden++) {
                    String stats = statsSection(reasonMask, hidden);
                    int boundary = boundaryDecisionLength(sectionMask, reasonMask, hidden);
                    String atBoundary = snapshot(
                            sectionMask, reasonMask, hidden, boundary).format();

                    assertEquals(
                            context + " hidden=" + hidden
                                    + " single-digit marker changed reserve length",
                            expectedStatsLength,
                            stats.length());
                    assertEquals(
                            context + " hidden=" + hidden
                                    + " single-digit marker moved the whole-snapshot boundary",
                            expectedBoundary,
                            boundary);
                    assertEquals(
                            context + " hidden=" + hidden
                                    + " exact boundary must remain normal",
                            expectedNormal(sectionMask, reasonMask, hidden, boundary),
                            atBoundary);
                    assertEquals(
                            context + " hidden=" + hidden
                                    + " exact boundary must consume hard ceiling",
                            RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET,
                            atBoundary.length());

                    assertStatsIdentity(
                            atBoundary, reasonMask, hidden,
                            context + " hidden=" + hidden);
                    assertGuardIdentity(
                            atBoundary, sectionMask,
                            context + " hidden=" + hidden);
                }
            }
        }
    }

    @Test
    public void firstMarkerReserveIsPaidOnlyByCoreOnceOverflowIsActive() {
        for (int sectionMask = 0; sectionMask < SECTION_COMBINATION_COUNT; sectionMask++) {
            for (int reasonMask = 1; reasonMask < REASON_COMBINATION_COUNT; reasonMask++) {
                int decisionLength =
                        boundaryDecisionLength(sectionMask, reasonMask, 0) + 12;
                String withoutMarker = snapshot(
                        sectionMask, reasonMask, 0, decisionLength).format();
                String withMarker = snapshot(
                        sectionMask, reasonMask, 1, decisionLength).format();
                String context = context(sectionMask, reasonMask);

                assertEquals(
                        context + " zero-hidden overflow rendering drifted",
                        expectedOverflow(sectionMask, reasonMask, 0, decisionLength),
                        withoutMarker);
                assertEquals(
                        context + " first-marker overflow rendering drifted",
                        expectedOverflow(sectionMask, reasonMask, 1, decisionLength),
                        withMarker);

                assertEquals(
                        context + " marker birth must cost exactly eight core characters",
                        corePart(withoutMarker).length() - MARKER_ONE_SUFFIX.length(),
                        corePart(withMarker).length());
                assertEquals(
                        context + " release changed while marker reserve grew",
                        releasePart(withoutMarker),
                        releasePart(withMarker));
                assertEquals(
                        context + " history/flap/latency tail changed while marker reserve grew",
                        nonStatsTailAfterStats(withoutMarker),
                        nonStatsTailAfterStats(withMarker));

                assertStatsIdentity(
                        withoutMarker, reasonMask, 0, context + " without marker");
                assertStatsIdentity(
                        withMarker, reasonMask, 1, context + " with marker");
                assertGuardIdentity(
                        withoutMarker, sectionMask, context + " without marker");
                assertGuardIdentity(
                        withMarker, sectionMask, context + " with marker");
            }
        }
    }

    @Test
    public void singleDigitMarkerPlateauKeepsOverflowCoreBudgetConstant() {
        for (int sectionMask = 0; sectionMask < SECTION_COMBINATION_COUNT; sectionMask++) {
            for (int reasonMask = 1; reasonMask < REASON_COMBINATION_COUNT; reasonMask++) {
                int decisionLength =
                        boundaryDecisionLength(sectionMask, reasonMask, 1) + 12;
                String baseline = snapshot(
                        sectionMask, reasonMask, 1, decisionLength).format();
                int baselineCoreLength = corePart(baseline).length();
                String baselineRelease = releasePart(baseline);
                String baselineTail = nonStatsTailAfterStats(baseline);
                String context = context(sectionMask, reasonMask);

                for (int hidden = 2; hidden <= 9; hidden++) {
                    String rendered = snapshot(
                            sectionMask, reasonMask, hidden, decisionLength).format();

                    assertEquals(
                            context + " hidden=" + hidden
                                    + " overflow rendering drifted",
                            expectedOverflow(sectionMask, reasonMask, hidden, decisionLength),
                            rendered);
                    assertEquals(
                            context + " hidden=" + hidden
                                    + " single-digit marker changed core budget",
                            baselineCoreLength,
                            corePart(rendered).length());
                    assertEquals(
                            context + " hidden=" + hidden
                                    + " release changed across marker plateau",
                            baselineRelease,
                            releasePart(rendered));
                    assertEquals(
                            context + " hidden=" + hidden
                                    + " tail changed across marker plateau",
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
        }
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
                - statsSection(reasonMask, hidden).length()
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
                + statsSection(reasonMask, hidden)
                + ((sectionMask & HISTORY) != 0 ? HISTORY_SECTION : "")
                + ((sectionMask & FLAP) != 0 ? FLAP_SECTION : "")
                + ((sectionMask & LATENCY) != 0 ? LATENCY_SECTION : "");
    }

    private static String expectedOverflow(
            int sectionMask, int reasonMask, int hidden, int decisionLength) {
        String stats = statsSection(reasonMask, hidden);
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
            out.append(" x").append(i);
        }
        return out.toString();
    }

    private static String statsSection(int reasonMask, int hidden) {
        StringBuilder out = new StringBuilder(" • stats{")
                .append(reasonLabels(reasonMask));
        if (hidden > 0) {
            out.append(" …more+").append(hidden);
        }
        return out.append('}').toString();
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
                statsSection(reasonMask, hidden),
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

        if (hidden == 0) {
            assertFalse(context + " unexpected omission marker", stats.contains("…more"));
        } else {
            assertTrue(
                    context + " missing exact omission marker",
                    stats.contains("…more+" + hidden));
        }
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
