package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsProducerIntegrationTest {
    private static final String STATS_PREFIX = " • stats{";
    private static final String OMISSION_PREFIX = "…more+";
    private static final String[] RELEASE_REASONS = {
            "rt-slowdown", "rt-speedup", "file-speedup", "file-slowdown"
    };
    private static final String[] DIAGNOSTIC_REASON_LABELS = {
            "rtslow", "rtfast", "ffast", "fslow"
    };
    private static final String RICH_GRAMMAR_SEQUENCE = "srsrsrsrssrsssr";
    private static final String RCONF_BUDGET_EDGE_SEQUENCE = "rsrssrsrsssrssrsrrssr";

    @Test
    public void emptyProducerDoesNotCreateStatsSection() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();

        String rendered = snapshot(stats.diagnostics(), false).format();

        assertEquals("", stats.diagnostics());
        assertEquals("route why=learning n=4/4", rendered);
    }

    @Test
    public void singleReasonProducerRoundTripsByteForByteUnderBudget() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "stable");
        stats.record("rt-slowdown", "reversal");
        stats.record("rt-slowdown", "superseded");
        stats.record("rt-slowdown", "superseded");

        String producer = stats.diagnostics();
        String content = statsContent(snapshot(producer, false).format());

        assertEquals("rel s/r/x rtslow 1/1/0 sup=2 sig=learn", producer);
        assertEquals(producer, content);
    }

    @Test
    public void fourReasonProducerGrammarCompactsWithoutLosingReasonIdentity() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (String reason : RELEASE_REASONS) {
            stats.record(reason, "stable");
            stats.record(reason, "reversal");
            stats.record(reason, "expired");
            stats.record(reason, "superseded");
        }

        String producer = stats.diagnostics();
        String content = statsContent(snapshot(producer, false).format());

        assertProducerReasonOrder(producer);
        assertAllReasonLabels(content);
        assertTrue(content.length() <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET);
        assertTrue(content.contains(OMISSION_PREFIX));
        assertExactOmissionCount(producer, content);
    }

    @Test
    public void realProducerSignalGrammarKeepsFairSignalCoverageAtFullStatsBudget() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (String reason : RELEASE_REASONS) {
            stats.record(reason, "stable");
            stats.record(reason, "stable");
            stats.record(reason, "stable");
            stats.record(reason, "reversal");
        }

        String producer = stats.diagnostics();
        String content = statsContent(snapshot(producer, false).format());

        assertProducerReasonOrder(producer);
        assertTrue(producer.contains("rev25%@4"));
        assertEquals(4, tokenCount(producer, "sig=stable"));
        assertAllReasonLabels(content);
        assertEquals(4, tokenCount(content, "sig=stable"));
        assertTrue(content.length() <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET);
        assertExactOmissionCount(producer, content);
    }

    @Test
    public void pathologicalSnapshotReservesRealProducerIdentityAndGuardTail() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (String reason : RELEASE_REASONS) {
            stats.record(reason, "stable");
            stats.record(reason, "reversal");
            stats.record(reason, "expired");
            stats.record(reason, "superseded");
        }

        String producer = stats.diagnostics();
        String rendered = snapshot(producer, true).format();
        String content = statsContent(rendered);

        assertTrue(rendered.length() <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);
        assertAllReasonLabels(content);
        assertTrue(content.contains(OMISSION_PREFIX));
        assertTrue(rendered.contains("guard="));
        assertExactOmissionCount(producer, content);
    }

    @Test
    public void realProducerFixtureCoversEveryDetailGrammarFamily() {
        RouteReleaseOutcomeStats stats = richGrammarStats();

        String producer = stats.diagnostics();
        String content = statsContent(snapshot(producer, false).format());

        assertEquals(
                "rel s/r/x rtslow 9/6/0 sup=1 rev37%@8 sig=mixed tr=2/4 cancel=4/0 "
                        + "conf33%@6/mixed rtr=1/1 rcancel=1/0 rmat=usable rconf50%@2",
                producer);
        assertDetailToken(producer, "sup=1");
        assertDetailToken(producer, "rev37%@8");
        assertDetailToken(producer, "sig=mixed");
        assertDetailToken(producer, "tr=2/4");
        assertDetailToken(producer, "cancel=4/0");
        assertDetailToken(producer, "conf33%@6/mixed");
        assertDetailToken(producer, "rtr=1/1");
        assertDetailToken(producer, "rcancel=1/0");
        assertDetailToken(producer, "rmat=usable");
        assertDetailToken(producer, "rconf50%@2");
        assertTrue(content.length() <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET);
        assertExactOmissionCount(producer, content);
    }

    @Test
    public void priorityCompactionKeepsRealSigRevConfAheadOfSecondaryDetails() {
        RouteReleaseOutcomeStats stats = richGrammarStats();
        stats.record("file-speedup", "stable");

        String producer = stats.diagnostics();
        String content = statsContent(snapshot(producer, false).format());

        assertEquals(
                "rel s/r/x rtslow 9/6/0 rev37%@8 sig=mixed conf33%@6/mixed "
                        + "ffast 1/0/0 sig=learn …more+7",
                content);
        assertDetailToken(content, "sig=mixed");
        assertDetailToken(content, "rev37%@8");
        assertDetailToken(content, "conf33%@6/mixed");
        assertFalse(content.contains("sup="));
        assertFalse(content.contains("tr="));
        assertFalse(content.contains("cancel="));
        assertFalse(content.contains("rtr="));
        assertFalse(content.contains("rcancel="));
        assertFalse(content.contains("rmat="));
        assertFalse(content.contains("rconf"));
        assertExactOmissionCount(producer, content);
    }

    @Test
    public void rconfPriorityUsesExactFinalCharacterBudgetSlot() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        recordDirectionalSequence(stats, "rt-slowdown", RCONF_BUDGET_EDGE_SEQUENCE);
        stats.record("file-speedup", "stable");

        String producer = stats.diagnostics();
        String content = statsContent(snapshot(producer, false).format());

        assertEquals(
                "rel s/r/x rtslow 12/9/0 rev50%@8 sig=risk tr=2/6 cancel=6/0 "
                        + "conf25%@8/weak rtr=0/2 rcancel=2/0 rmat=usable rconf0%@2 "
                        + "ffast 1/0/0 sig=learn",
                producer);
        assertEquals(
                "rel s/r/x rtslow 12/9/0 rev50%@8 sig=risk conf25%@8/weak rconf0%@2 "
                        + "ffast 1/0/0 sig=learn …more+5",
                content);
        assertEquals(RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET, content.length());
        assertDetailToken(content, "sig=risk");
        assertDetailToken(content, "rev50%@8");
        assertDetailToken(content, "conf25%@8/weak");
        assertDetailToken(content, "rconf0%@2");
        assertFalse(content.contains("tr="));
        assertFalse(content.contains("cancel="));
        assertFalse(content.contains("rtr="));
        assertFalse(content.contains("rcancel="));
        assertFalse(content.contains("rmat="));
        assertExactOmissionCount(producer, content);
    }

    private static RouteReleaseOutcomeStats richGrammarStats() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        recordDirectionalSequence(stats, "rt-slowdown", RICH_GRAMMAR_SEQUENCE);
        stats.record("rt-slowdown", "superseded");
        return stats;
    }

    private static void recordDirectionalSequence(
            RouteReleaseOutcomeStats stats, String releaseReason, String sequence) {
        for (int i = 0; i < sequence.length(); i++) {
            char outcome = sequence.charAt(i);
            if (outcome == 's') {
                stats.record(releaseReason, "stable");
            } else if (outcome == 'r') {
                stats.record(releaseReason, "reversal");
            } else {
                throw new AssertionError("unknown fixture outcome: " + outcome);
            }
        }
    }

    private static RouteDecisionDiagnosticsSnapshot snapshot(
            String releaseBreakdown, boolean pathologicalPressure) {
        if (!pathologicalPressure) {
            return new RouteDecisionDiagnosticsSnapshot(
                    "learning", 4, 4,
                    false, "-", "-", 0, 3,
                    releaseBreakdown,
                    "-", 0,
                    0, 0L,
                    false, Long.MIN_VALUE, Long.MAX_VALUE, 0L, 0L);
        }

        String longLabel = "integration-" + "x".repeat(320);
        return new RouteDecisionDiagnosticsSnapshot(
                longLabel, Integer.MAX_VALUE, Integer.MAX_VALUE,
                true, longLabel, "pending", Integer.MAX_VALUE, Integer.MAX_VALUE,
                releaseBreakdown,
                longLabel, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Long.MAX_VALUE,
                true, Long.MAX_VALUE - 1L, Long.MAX_VALUE - 2L,
                Long.MAX_VALUE, Long.MAX_VALUE);
    }

    private static String statsContent(String snapshot) {
        int start = snapshot.indexOf(STATS_PREFIX);
        assertTrue("snapshot must contain stats section: " + snapshot, start >= 0);
        start += STATS_PREFIX.length();
        int end = snapshot.indexOf('}', start);
        assertTrue("stats section must close: " + snapshot, end >= start);
        return snapshot.substring(start, end);
    }

    private static void assertProducerReasonOrder(String producer) {
        int previous = -1;
        for (String label : DIAGNOSTIC_REASON_LABELS) {
            int index = producer.indexOf(label);
            assertTrue("producer missing reason " + label + ": " + producer, index > previous);
            previous = index;
        }
    }

    private static void assertAllReasonLabels(String value) {
        for (String label : DIAGNOSTIC_REASON_LABELS) {
            assertTrue("missing reason " + label + ": " + value, tokenCount(value, label) == 1);
        }
    }

    private static void assertDetailToken(String value, String expected) {
        assertEquals("missing detail token " + expected + ": " + value, 1, tokenCount(value, expected));
    }

    private static void assertExactOmissionCount(String producer, String compacted) {
        String[] sourceTokens = tokens(producer);
        String[] compactedTokens = tokens(compacted);
        int omitted = -1;
        int visible = 0;
        for (String token : compactedTokens) {
            if (token.startsWith(OMISSION_PREFIX)) {
                omitted = Integer.parseInt(token.substring(OMISSION_PREFIX.length()));
            } else {
                visible++;
            }
        }

        if (omitted < 0) {
            assertEquals(sourceTokens.length, visible);
        } else {
            assertEquals(sourceTokens.length - visible, omitted);
        }
    }

    private static int tokenCount(String value, String expected) {
        int count = 0;
        for (String token : tokens(value)) {
            if (expected.equals(token)) count++;
        }
        return count;
    }

    private static String[] tokens(String value) {
        if (value == null || value.isEmpty()) return new String[0];
        return value.trim().split(" +");
    }
}
