package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsSectionPresencePermutationTest {
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

    @Test
    public void allSectionPresencePermutationsUseStableOverflowBudgets() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            Fixture fixture = fixture(mask);
            String first = fixture.snapshot.format();
            String second = fixture.snapshot.format();
            String expected = expected(fixture, mask);
            String context = "mask=" + binaryMask(mask) + " rendered=" + first;

            assertEquals(context, first, second);
            assertEquals(context, expected, first);
            assertTrue(
                    context + " exceeded snapshot ceiling",
                    first.length() <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);

            assertSectionPresence(first, mask, context);
            if ((mask & STATS) != 0) {
                for (String reason : REASON_LABELS) {
                    assertTrue(context + " missing reason " + reason, first.contains(reason));
                }
                assertTrue(context + " missing omission marker", first.contains("…more+6"));
            }
            if ((mask & LATENCY) != 0) {
                assertTrue(context + " missing guard tail", first.contains(
                        "guard=" + Long.MAX_VALUE + "/" + Long.MAX_VALUE + "ms"));
            }
        }
    }

    @Test
    public void removingAnyNonStatsSectionNeverRedistributesItsBudget() {
        for (int mask = 0; mask < COMBINATION_COUNT; mask++) {
            for (int bit : new int[]{RELEASE, HISTORY, FLAP, LATENCY}) {
                if ((mask & bit) == 0) continue;

                Fixture withSection = fixture(mask);
                Fixture withoutSection = fixture(mask & ~bit);
                String withRendered = withSection.snapshot.format();
                String withoutRendered = withoutSection.snapshot.format();
                String context = "mask=" + binaryMask(mask)
                        + " remove=" + sectionName(bit);

                assertEquals(context + " baseline mismatch",
                        expected(withSection, mask), withRendered);
                assertEquals(context + " removal mismatch",
                        expected(withoutSection, mask & ~bit), withoutRendered);

                boolean statsPresent = (mask & STATS) != 0;
                assertEquals(
                        context + " core budget changed after freeing another section",
                        expectedCore(withSection, statsPresent),
                        expectedCore(withoutSection, statsPresent));

                if ((mask & RELEASE) != 0 && bit != RELEASE) {
                    assertTrue(context + " release representation drifted",
                            withRendered.contains(expectedRelease(withSection, statsPresent)));
                    assertTrue(context + " release representation changed after removal",
                            withoutRendered.contains(expectedRelease(withoutSection, statsPresent)));
                }
                if ((mask & HISTORY) != 0 && bit != HISTORY) {
                    assertTrue(context + " history representation drifted",
                            withRendered.contains(expectedHistory(withSection, statsPresent)));
                    assertTrue(context + " history representation changed after removal",
                            withoutRendered.contains(expectedHistory(withoutSection, statsPresent)));
                }
                if ((mask & FLAP) != 0 && bit != FLAP) {
                    assertTrue(context + " flap representation drifted",
                            withRendered.contains(expectedFlap(withSection, statsPresent)));
                    assertTrue(context + " flap representation changed after removal",
                            withoutRendered.contains(expectedFlap(withoutSection, statsPresent)));
                }
                if ((mask & LATENCY) != 0 && bit != LATENCY) {
                    assertTrue(context + " latency representation drifted",
                            withRendered.contains(expectedLatency(withSection, statsPresent)));
                    assertTrue(context + " latency representation changed after removal",
                            withoutRendered.contains(expectedLatency(withoutSection, statsPresent)));
                }
            }
        }
    }

    @Test
    public void togglingStatsIsTheOnlyPresenceChangeAllowedToDiscountOtherSections() {
        for (int baseMask = 0; baseMask < (1 << 4); baseMask++) {
            Fixture withoutStats = fixture(baseMask);
            Fixture withStats = fixture(baseMask | STATS);
            String plain = withoutStats.snapshot.format();
            String reserved = withStats.snapshot.format();
            String context = "baseMask=" + binaryMask(baseMask);

            assertEquals(context, expected(withoutStats, baseMask), plain);
            assertEquals(context, expected(withStats, baseMask | STATS), reserved);

            assertEquals(context + " stats reserve did not use locked core budget",
                    bounded(withStats.core, statsCoreBudget(), false),
                    beforeOptionalSections(reserved));

            if ((baseMask & RELEASE) != 0) {
                assertTrue(context, reserved.contains(expectedRelease(withStats, true)));
            }
            if ((baseMask & HISTORY) != 0) {
                assertTrue(context, reserved.contains(expectedHistory(withStats, true)));
            }
            if ((baseMask & FLAP) != 0) {
                assertTrue(context, reserved.contains(expectedFlap(withStats, true)));
            }
            if ((baseMask & LATENCY) != 0) {
                assertTrue(context, reserved.contains(expectedLatency(withStats, true)));
                assertTrue(context, reserved.contains(
                        "guard=" + Long.MAX_VALUE + "/" + Long.MAX_VALUE + "ms"));
            }
            for (String reason : REASON_LABELS) {
                assertTrue(context + " missing stats identity " + reason, reserved.contains(reason));
            }
        }
    }

    private static Fixture fixture(int mask) {
        String decision = "C".repeat(220);
        String releaseReason = "R".repeat(220);
        String history = "H".repeat(220);
        int samples = Integer.MAX_VALUE;
        int streak = Integer.MAX_VALUE;
        long flapExtra = Long.MAX_VALUE;
        long gap = Long.MAX_VALUE - 1L;
        long need = Long.MAX_VALUE - 2L;
        long guard = Long.MAX_VALUE;

        boolean hasRelease = (mask & RELEASE) != 0;
        boolean hasHistory = (mask & HISTORY) != 0;
        boolean hasFlap = (mask & FLAP) != 0;
        boolean hasLatency = (mask & LATENCY) != 0;
        boolean hasStats = (mask & STATS) != 0;

        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                decision, samples, samples,
                hasRelease, hasRelease ? releaseReason : null,
                "pending", streak, streak,
                hasStats ? BREAKDOWN : null,
                hasHistory ? history : null, hasHistory ? streak : 0,
                hasFlap ? Integer.MAX_VALUE : 0, flapExtra,
                hasLatency, gap, need,
                guard, guard);

        String core = "route why=" + decision + " n=" + samples + "/" + samples;
        String release = hasRelease
                ? " • release=" + releaseReason + ":pending×" + streak + "/" + streak
                : "";
        String historySection = hasHistory
                ? " • hist=" + history + " stable×" + streak + "/" + streak
                : "";
        String flap = hasFlap
                ? " • flap×" + Integer.MAX_VALUE + "(+" + flapExtra + "ms)"
                : "";
        String latency = hasLatency
                ? " • lat gap=" + gap + "ms need=" + need
                        + "ms guard=" + guard + "/" + guard + "ms"
                : "";

        return new Fixture(snapshot, core, release, historySection, flap, latency);
    }

    private static String expected(Fixture fixture, int mask) {
        boolean hasStats = (mask & STATS) != 0;
        int coreBudget = hasStats ? statsCoreBudget() : CORE_BASE_BUDGET;
        int releaseBudget = hasStats
                ? RELEASE_BASE_BUDGET - RELEASE_STATS_DISCOUNT
                : RELEASE_BASE_BUDGET;
        int historyBudget = hasStats
                ? HISTORY_BASE_BUDGET - HISTORY_STATS_DISCOUNT
                : HISTORY_BASE_BUDGET;
        int flapBudget = hasStats
                ? FLAP_BASE_BUDGET - FLAP_STATS_DISCOUNT
                : FLAP_BASE_BUDGET;
        int latencyBudget = hasStats
                ? LATENCY_BASE_BUDGET - LATENCY_STATS_DISCOUNT
                : LATENCY_BASE_BUDGET;

        return bounded(fixture.core, coreBudget, false)
                + bounded(fixture.release, releaseBudget, false)
                + (hasStats ? OMITTED_STATS : "")
                + bounded(fixture.history, historyBudget, false)
                + bounded(fixture.flap, flapBudget, false)
                + bounded(fixture.latency, latencyBudget, true);
    }

    private static int statsCoreBudget() {
        return CORE_BASE_BUDGET
                - CORE_STATS_DISCOUNT
                - Math.max(0, OMITTED_STATS.length() - BASE_STATS_RESERVE);
    }

    private static String expectedCore(Fixture fixture, boolean statsPresent) {
        return bounded(
                fixture.core,
                statsPresent ? statsCoreBudget() : CORE_BASE_BUDGET,
                false);
    }

    private static String expectedRelease(Fixture fixture, boolean statsPresent) {
        return bounded(
                fixture.release,
                statsPresent
                        ? RELEASE_BASE_BUDGET - RELEASE_STATS_DISCOUNT
                        : RELEASE_BASE_BUDGET,
                false);
    }

    private static String expectedHistory(Fixture fixture, boolean statsPresent) {
        return bounded(
                fixture.history,
                statsPresent
                        ? HISTORY_BASE_BUDGET - HISTORY_STATS_DISCOUNT
                        : HISTORY_BASE_BUDGET,
                false);
    }

    private static String expectedFlap(Fixture fixture, boolean statsPresent) {
        return bounded(
                fixture.flap,
                statsPresent
                        ? FLAP_BASE_BUDGET - FLAP_STATS_DISCOUNT
                        : FLAP_BASE_BUDGET,
                false);
    }

    private static String expectedLatency(Fixture fixture, boolean statsPresent) {
        return bounded(
                fixture.latency,
                statsPresent
                        ? LATENCY_BASE_BUDGET - LATENCY_STATS_DISCOUNT
                        : LATENCY_BASE_BUDGET,
                true);
    }

    private static void assertSectionPresence(String rendered, int mask, String context) {
        assertEquals(context + " release presence",
                (mask & RELEASE) != 0, rendered.contains(" • release="));
        assertEquals(context + " history presence",
                (mask & HISTORY) != 0, rendered.contains(" • hist="));
        assertEquals(context + " flap presence",
                (mask & FLAP) != 0, rendered.contains(" • flap×"));
        assertEquals(context + " latency presence",
                (mask & LATENCY) != 0, rendered.contains(" • lat"));
        assertEquals(context + " stats presence",
                (mask & STATS) != 0, rendered.contains(" • stats{"));

        if ((mask & LATENCY) == 0) {
            assertFalse(context + " unexpected guard tail", rendered.contains("guard="));
        }
    }

    private static String beforeOptionalSections(String rendered) {
        int cut = rendered.length();
        for (String marker : new String[]{
                " • release=", " • stats{", " • hist=", " • flap×", " • lat"}) {
            int index = rendered.indexOf(marker);
            if (index >= 0) cut = Math.min(cut, index);
        }
        return rendered.substring(0, cut);
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

    private static final class Fixture {
        final RouteDecisionDiagnosticsSnapshot snapshot;
        final String core;
        final String release;
        final String history;
        final String flap;
        final String latency;

        Fixture(
                RouteDecisionDiagnosticsSnapshot snapshot,
                String core,
                String release,
                String history,
                String flap,
                String latency) {
            this.snapshot = snapshot;
            this.core = core;
            this.release = release;
            this.history = history;
            this.flap = flap;
            this.latency = latency;
        }
    }
}
