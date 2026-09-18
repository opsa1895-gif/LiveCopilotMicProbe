package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsNormalOverflowBoundaryTest {
    private static final int RELEASE = 1;
    private static final int HISTORY = 1 << 1;
    private static final int FLAP = 1 << 2;
    private static final int LATENCY = 1 << 3;
    private static final int STATS = 1 << 4;
    private static final int COMBINATION_COUNT = 1 << 5;

    private static final int CORE_BASE_BUDGET = 52;
    private static final int CORE_STATS_DISCOUNT = 4;
    private static final int BASE_STATS_RESERVE = 15;

    private static final String BREAKDOWN =
            "rel s/r/x "
                    + "rtslow 1/0/0 "
                    + "rtfast 2/0/0 "
                    + "ffast 3/0/0 "
                    + "fslow 4/0/0";
    private static final String OMITTED_STATS =
            " • stats{rtslow rtfast ffast fslow …more+6}";
    private static final String[] REASON_LABELS = {
            "rtslow", "rtfast", "ffast", "fslow"
    };

    private static final String CORE_PREFIX = "route why=";
    private static final String CORE_SUFFIX = " n=1/2";
    private static final String RELEASE_SECTION = " • release=r:ok";
    private static final String HISTORY_SECTION = " • hist=h";
    private static final String FLAP_SECTION = " • flap×1(+0ms)";
    private static final String LATENCY_SECTION = " • lat guard=1/2ms";

    @Test
    public void everyPresenceCombinationSwitchesExactlyOneCharacterPastBoundary() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            int boundaryLength = boundaryDecisionLength(mask);
            String atBoundary = snapshot(mask, boundaryLength).format();
            String overBoundary = snapshot(mask, boundaryLength + 1).format();
            String context = "mask=" + binaryMask(mask)
                    + " boundaryDecisionLength=" + boundaryLength;

            assertTrue(context + " invalid boundary length", boundaryLength > 0);

            assertEquals(
                    context + " exact boundary must remain on the normal path",
                    expectedNormal(mask, boundaryLength),
                    atBoundary);
            assertEquals(
                    context + " boundary snapshot must consume the exact hard ceiling",
                    RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET,
                    atBoundary.length());

            assertEquals(
                    context + " +1 character must switch to the bounded overflow path",
                    expectedOverflow(mask, boundaryLength + 1),
                    overBoundary);
            assertTrue(
                    context + " overflow exceeded the hard ceiling",
                    overBoundary.length()
                            <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);

            String fullBoundaryDecision = "D".repeat(boundaryLength);
            String fullOverflowDecision = "D".repeat(boundaryLength + 1);
            assertTrue(
                    context + " normal boundary lost the full decision label",
                    atBoundary.contains(fullBoundaryDecision));
            assertFalse(
                    context + " overflow path failed to truncate the decision label",
                    overBoundary.contains(fullOverflowDecision));

            assertProtectedIdentity(atBoundary, mask, context + " normal");
            assertProtectedIdentity(overBoundary, mask, context + " overflow");
        }
    }

    @Test
    public void removingEachDescriptiveSectionMovesBoundaryByItsExactRawLength() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            for (int bit : new int[]{RELEASE, HISTORY, FLAP, LATENCY}) {
                if ((mask & bit) == 0) continue;

                int withSection = boundaryDecisionLength(mask);
                int withoutSection = boundaryDecisionLength(mask & ~bit);
                String context = "mask=" + binaryMask(mask)
                        + " remove=" + sectionName(bit);

                assertEquals(
                        context + " boundary shift did not equal the removed raw section length",
                        rawSectionLength(bit),
                        withoutSection - withSection);
            }
        }
    }

    @Test
    public void enablingStatsMovesBoundaryByExactlyTheRequiredStatsReserve() {
        for (int baseMask = 0; baseMask < (1 << 4); baseMask++) {
            int withoutStats = boundaryDecisionLength(baseMask);
            int withStats = boundaryDecisionLength(baseMask | STATS);
            String context = "baseMask=" + binaryMask(baseMask);

            assertEquals(
                    context + " stats boundary shift must equal the exact omitted stats reserve",
                    OMITTED_STATS.length(),
                    withoutStats - withStats);

            String atStatsBoundary = snapshot(baseMask | STATS, withStats).format();
            String overStatsBoundary = snapshot(baseMask | STATS, withStats + 1).format();
            for (String reason : REASON_LABELS) {
                assertTrue(context + " normal boundary missing reason " + reason,
                        atStatsBoundary.contains(reason));
                assertTrue(context + " overflow boundary missing reason " + reason,
                        overStatsBoundary.contains(reason));
            }
        }
    }

    @Test
    public void latencyGuardSurvivesBothSidesOfEveryReachableBoundary() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            if ((mask & LATENCY) == 0) continue;

            int boundaryLength = boundaryDecisionLength(mask);
            String atBoundary = snapshot(mask, boundaryLength).format();
            String overBoundary = snapshot(mask, boundaryLength + 1).format();
            String context = "mask=" + binaryMask(mask);

            assertTrue(context + " normal boundary lost guard tail",
                    atBoundary.contains("guard=1/2ms"));
            assertTrue(context + " overflow boundary lost guard tail",
                    overBoundary.contains("guard=1/2ms"));
        }
    }

    private static RouteDecisionDiagnosticsSnapshot snapshot(int mask, int decisionLength) {
        boolean hasRelease = (mask & RELEASE) != 0;
        boolean hasHistory = (mask & HISTORY) != 0;
        boolean hasFlap = (mask & FLAP) != 0;
        boolean hasLatency = (mask & LATENCY) != 0;
        boolean hasStats = (mask & STATS) != 0;

        return new RouteDecisionDiagnosticsSnapshot(
                "D".repeat(decisionLength), 1, 2,
                hasRelease, hasRelease ? "r" : null, "ok", 0, 1,
                hasStats ? BREAKDOWN : null,
                hasHistory ? "h" : null, 0,
                hasFlap ? 1 : 0, 0L,
                hasLatency, Long.MIN_VALUE, Long.MAX_VALUE,
                1L, 2L);
    }

    private static int boundaryDecisionLength(int mask) {
        int statsReserve = (mask & STATS) != 0 ? OMITTED_STATS.length() : 0;
        int nonCoreLength = rawOptionalSectionsLength(mask);
        int fixedCoreLength = CORE_PREFIX.length() + CORE_SUFFIX.length();

        return RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET
                - statsReserve
                - nonCoreLength
                - fixedCoreLength;
    }

    private static int rawOptionalSectionsLength(int mask) {
        int length = 0;
        if ((mask & RELEASE) != 0) length += RELEASE_SECTION.length();
        if ((mask & HISTORY) != 0) length += HISTORY_SECTION.length();
        if ((mask & FLAP) != 0) length += FLAP_SECTION.length();
        if ((mask & LATENCY) != 0) length += LATENCY_SECTION.length();
        return length;
    }

    private static String expectedNormal(int mask, int decisionLength) {
        return rawCore(decisionLength)
                + ((mask & RELEASE) != 0 ? RELEASE_SECTION : "")
                + ((mask & STATS) != 0 ? OMITTED_STATS : "")
                + ((mask & HISTORY) != 0 ? HISTORY_SECTION : "")
                + ((mask & FLAP) != 0 ? FLAP_SECTION : "")
                + ((mask & LATENCY) != 0 ? LATENCY_SECTION : "");
    }

    private static String expectedOverflow(int mask, int decisionLength) {
        boolean hasStats = (mask & STATS) != 0;
        int coreBudget = CORE_BASE_BUDGET;
        if (hasStats) {
            coreBudget -= CORE_STATS_DISCOUNT;
            coreBudget -= Math.max(0, OMITTED_STATS.length() - BASE_STATS_RESERVE);
        }

        return bounded(rawCore(decisionLength), coreBudget)
                + ((mask & RELEASE) != 0 ? RELEASE_SECTION : "")
                + (hasStats ? OMITTED_STATS : "")
                + ((mask & HISTORY) != 0 ? HISTORY_SECTION : "")
                + ((mask & FLAP) != 0 ? FLAP_SECTION : "")
                + ((mask & LATENCY) != 0 ? LATENCY_SECTION : "");
    }

    private static String rawCore(int decisionLength) {
        return CORE_PREFIX + "D".repeat(decisionLength) + CORE_SUFFIX;
    }

    private static void assertProtectedIdentity(String rendered, int mask, String context) {
        if ((mask & STATS) != 0) {
            assertTrue(context + " missing stats section", rendered.contains(" • stats{"));
            assertTrue(context + " missing omission count", rendered.contains("…more+6"));
            for (String reason : REASON_LABELS) {
                assertTrue(context + " missing reason " + reason, rendered.contains(reason));
            }
        } else {
            assertFalse(context + " unexpected stats section", rendered.contains(" • stats{"));
        }

        if ((mask & LATENCY) != 0) {
            assertTrue(context + " missing latency guard", rendered.contains("guard=1/2ms"));
        } else {
            assertFalse(context + " unexpected latency guard", rendered.contains("guard="));
        }
    }

    private static String bounded(String value, int budget) {
        if (value == null || value.isEmpty() || budget <= 0) return "";
        if (value.length() <= budget) return value;
        if (budget == 1) return "…";
        return value.substring(0, budget - 1) + '…';
    }

    private static int rawSectionLength(int bit) {
        if (bit == RELEASE) return RELEASE_SECTION.length();
        if (bit == HISTORY) return HISTORY_SECTION.length();
        if (bit == FLAP) return FLAP_SECTION.length();
        if (bit == LATENCY) return LATENCY_SECTION.length();
        throw new AssertionError("unknown section bit " + bit);
    }

    private static String sectionName(int bit) {
        if (bit == RELEASE) return "release";
        if (bit == HISTORY) return "history";
        if (bit == FLAP) return "flap";
        if (bit == LATENCY) return "latency";
        throw new AssertionError("unknown section bit " + bit);
    }

    private static String binaryMask(int mask) {
        String value = Integer.toBinaryString(mask);
        return "00000".substring(Math.min(5, value.length())) + value;
    }
}
