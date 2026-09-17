package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
