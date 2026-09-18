package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsSectionPriorityTest {
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

    @Test
    public void noStatsOverflowUsesLockedBaselineSectionBudgets() {
        Fixture fixture = fixture(null);
        String actual = fixture.snapshot.format();

        String expected = bounded(fixture.core, CORE_BASE_BUDGET, false)
                + bounded(fixture.release, RELEASE_BASE_BUDGET, false)
                + bounded(fixture.history, HISTORY_BASE_BUDGET, false)
                + bounded(fixture.flap, FLAP_BASE_BUDGET, false)
                + bounded(fixture.latency, LATENCY_BASE_BUDGET, true);

        assertEquals(expected, actual);
        assertEquals(RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET, actual.length());
        assertTrue(actual.contains("route why="));
        assertTrue(actual.contains("release="));
        assertTrue(actual.contains("hist="));
        assertTrue(actual.contains("flap×"));
        assertTrue(actual.contains("lat"));
        assertTrue(actual.contains("guard="));
    }

    @Test
    public void statsIdentityReserveShrinksDescriptiveBudgetsBeforeReasonIdentityOrGuard() {
        String breakdown = "rel s/r/x "
                + "rtslow 1/1/0 sig=a rev10%@8 "
                + "rtfast 2/2/0 sig=b rev20%@8 "
                + "ffast 3/3/0 sig=c rev30%@8 "
                + "fslow 4/4/0 sig=d rev40%@8";
        Fixture fixture = fixture(breakdown);
        String actual = fixture.snapshot.format();
        String omittedStats = expectedOmittedStats(breakdown);

        int extraStatsReserve = Math.max(0, omittedStats.length() - BASE_STATS_RESERVE);
        int coreBudget = CORE_BASE_BUDGET - CORE_STATS_DISCOUNT - extraStatsReserve;
        int releaseBudget = RELEASE_BASE_BUDGET - RELEASE_STATS_DISCOUNT;
        int historyBudget = HISTORY_BASE_BUDGET - HISTORY_STATS_DISCOUNT;
        int flapBudget = FLAP_BASE_BUDGET - FLAP_STATS_DISCOUNT;
        int latencyBudget = LATENCY_BASE_BUDGET - LATENCY_STATS_DISCOUNT;

        String expected = bounded(fixture.core, coreBudget, false)
                + bounded(fixture.release, releaseBudget, false)
                + omittedStats
                + bounded(fixture.history, historyBudget, false)
                + bounded(fixture.flap, flapBudget, false)
                + bounded(fixture.latency, latencyBudget, true);

        assertEquals(expected, actual);
        assertTrue(actual.length() <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);
        for (String reason : REASON_LABELS) {
            assertTrue("missing stats reason identity " + reason, actual.contains(reason));
        }
        assertTrue(actual.contains("guard=" + Long.MAX_VALUE + "/" + Long.MAX_VALUE + "ms"));
    }

    @Test
    public void omissionMarkerDigitGrowthConsumesOnlyCoreBudget() {
        String nineHidden = breakdownWithHiddenTokens(9);
        String tenHidden = breakdownWithHiddenTokens(10);

        String nine = fixture(nineHidden).snapshot.format();
        String ten = fixture(tenHidden).snapshot.format();

        String nineCore = before(nine, " • release=");
        String tenCore = before(ten, " • release=");
        String nineRelease = between(nine, " • release=", " • stats{");
        String tenRelease = between(ten, " • release=", " • stats{");
        String nineTail = afterStats(nine);
        String tenTail = afterStats(ten);

        assertEquals(nineCore.length() - 1, tenCore.length());
        assertEquals(nineRelease, tenRelease);
        assertEquals(nineTail, tenTail);
        assertTrue(nine.contains("…more+9"));
        assertTrue(ten.contains("…more+10"));
        assertTrue(nine.contains("guard="));
        assertTrue(ten.contains("guard="));
        for (String reason : REASON_LABELS) {
            assertTrue(nine.contains(reason));
            assertTrue(ten.contains(reason));
        }
    }

    private static Fixture fixture(String breakdown) {
        String decision = "C".repeat(220);
        String releaseReason = "R".repeat(220);
        String history = "H".repeat(220);

        int samples = Integer.MAX_VALUE;
        int streak = Integer.MAX_VALUE;
        long flapExtra = Long.MAX_VALUE;
        long gap = Long.MAX_VALUE - 1L;
        long need = Long.MAX_VALUE - 2L;
        long guard = Long.MAX_VALUE;

        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                decision, samples, samples,
                true, releaseReason, "pending", streak, streak,
                breakdown,
                history, streak,
                Integer.MAX_VALUE, flapExtra,
                true, gap, need,
                guard, guard);

        String core = "route why=" + decision + " n=" + samples + "/" + samples;
        String release = " • release=" + releaseReason
                + ":pending×" + streak + "/" + streak;
        String historySection = " • hist=" + history
                + " stable×" + streak + "/" + streak;
        String flap = " • flap×" + Integer.MAX_VALUE + "(+" + flapExtra + "ms)";
        String latency = " • lat gap=" + gap + "ms need=" + need
                + "ms guard=" + guard + "/" + guard + "ms";

        return new Fixture(snapshot, core, release, historySection, flap, latency);
    }

    private static String expectedOmittedStats(String breakdown) {
        int omitted = tokens(breakdown).length - REASON_LABELS.length;
        return " • stats{rtslow rtfast ffast fslow …more+" + omitted + "}";
    }

    private static String breakdownWithHiddenTokens(int hidden) {
        StringBuilder out = new StringBuilder("rtslow rtfast ffast fslow");
        for (int i = 0; i < hidden; i++) {
            out.append(" x").append(i);
        }
        return out.toString();
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

    private static String before(String value, String marker) {
        int index = value.indexOf(marker);
        assertTrue("missing marker " + marker + " in " + value, index >= 0);
        return value.substring(0, index);
    }

    private static String between(String value, String startMarker, String endMarker) {
        int start = value.indexOf(startMarker);
        int end = value.indexOf(endMarker, start + startMarker.length());
        assertTrue("missing start marker " + startMarker, start >= 0);
        assertTrue("missing end marker " + endMarker, end >= 0);
        return value.substring(start, end);
    }

    private static String afterStats(String value) {
        int start = value.indexOf(" • stats{");
        assertTrue("missing stats section: " + value, start >= 0);
        int end = value.indexOf('}', start);
        assertTrue("unclosed stats section: " + value, end >= 0);
        return value.substring(end + 1);
    }

    private static String[] tokens(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
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
