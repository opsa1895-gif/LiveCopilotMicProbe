package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsPendingSnapshotPressureTest {
    private static final String STATS_PREFIX = " • stats{";
    private static final String OMISSION_PREFIX = "…more+";
    private static final String PENDING_SIGNAL_SEQUENCE = "sssrr";
    private static final String PENDING_RELIABILITY_SEQUENCE = "ssrsrrsrs";
    private static final String PENDING_SIGNAL_TOKEN = "sig=stable>mixed×1/2";
    private static final String PENDING_CONF_TOKEN = "conf33%@3/weak>mixed×1/2";
    private static final String[] RELEASE_REASONS = {
            "rt-slowdown", "rt-speedup", "file-speedup", "file-slowdown"
    };
    private static final String[] REASON_LABELS = {
            "rtslow", "rtfast", "ffast", "fslow"
    };
    private static final int[] PRESSURE_LENGTHS = {
            0, 8, 16, 24, 32, 48, 64, 96, 160, 320
    };

    @Test
    public void fourPendingSignalsRemainAtomicMonotonicAndGuardedAcrossSnapshotPressure() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (String reason : RELEASE_REASONS) {
            recordDirectionalSequence(stats, reason, PENDING_SIGNAL_SEQUENCE);
        }
        String producer = stats.diagnostics();

        int previousCoverage = Integer.MAX_VALUE;
        int firstCoverage = -1;
        int lastCoverage = -1;
        for (int pressure : PRESSURE_LENGTHS) {
            String first = pressuredSnapshot(producer, pressure).format();
            String second = pressuredSnapshot(producer, pressure).format();
            String content = statsContent(first);
            String context = "pressure=" + pressure + " rendered=" + first;

            assertEquals(context, first, second);
            assertSnapshotInvariants(producer, content, first, context);
            for (String reason : REASON_LABELS) {
                assertTrue(context + " missing reason " + reason, content.contains(reason));
            }

            int coverage = tokenCount(content, PENDING_SIGNAL_TOKEN);
            assertTrue(
                    context + " pending-signal coverage increased under more pressure from "
                            + previousCoverage + " to " + coverage,
                    coverage <= previousCoverage);
            if (firstCoverage < 0) firstCoverage = coverage;
            lastCoverage = coverage;
            previousCoverage = coverage;
        }

        assertTrue("low-pressure snapshot should preserve pending-signal detail",
                firstCoverage > 0);
        assertEquals("hard-overflow snapshot should fall back to reason identity", 0, lastCoverage);
    }

    @Test
    public void everyPendingReliabilityPairStaysAtomicMonotonicAndGuardedAcrossSnapshotPressure() {
        for (int firstReason = 0; firstReason < RELEASE_REASONS.length; firstReason++) {
            for (int secondReason = firstReason + 1;
                    secondReason < RELEASE_REASONS.length;
                    secondReason++) {
                RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
                recordDirectionalSequence(
                        stats, RELEASE_REASONS[firstReason], PENDING_RELIABILITY_SEQUENCE);
                recordDirectionalSequence(
                        stats, RELEASE_REASONS[secondReason], PENDING_RELIABILITY_SEQUENCE);
                String producer = stats.diagnostics();

                int previousCoverage = Integer.MAX_VALUE;
                int firstCoverage = -1;
                int lastCoverage = -1;
                for (int pressure : PRESSURE_LENGTHS) {
                    String rendered = pressuredSnapshot(producer, pressure).format();
                    String content = statsContent(rendered);
                    String context = "pair=" + firstReason + "/" + secondReason
                            + " pressure=" + pressure + " rendered=" + rendered;

                    assertSnapshotInvariants(producer, content, rendered, context);
                    assertTrue(context, content.contains(REASON_LABELS[firstReason]));
                    assertTrue(context, content.contains(REASON_LABELS[secondReason]));

                    int coverage = tokenCount(content, PENDING_CONF_TOKEN);
                    assertTrue(
                            context + " pending-conf coverage increased under more pressure from "
                                    + previousCoverage + " to " + coverage,
                            coverage <= previousCoverage);
                    if (firstCoverage < 0) firstCoverage = coverage;
                    lastCoverage = coverage;
                    previousCoverage = coverage;
                }

                String pairContext = "pair=" + firstReason + "/" + secondReason;
                assertEquals(pairContext + " low-pressure pending-conf coverage", 1, firstCoverage);
                assertEquals(pairContext + " hard-overflow pending-conf coverage", 0, lastCoverage);
            }
        }
    }

    @Test
    public void hardOverflowKeepsEverySectionIdentityAlongsidePendingReasonIdentity() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (String reason : RELEASE_REASONS) {
            recordDirectionalSequence(stats, reason, PENDING_SIGNAL_SEQUENCE);
        }
        String producer = stats.diagnostics();
        String rendered = pressuredSnapshot(producer, 320).format();
        String content = statsContent(rendered);

        assertTrue(
                "hard overflow exceeded snapshot budget: " + rendered.length(),
                rendered.length() <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);
        assertTrue(rendered.contains("route why="));
        assertTrue(rendered.contains("release="));
        assertTrue(rendered.contains("hist="));
        assertTrue(rendered.contains("flap×"));
        assertTrue(rendered.contains("lat"));
        assertTrue(rendered.contains("guard="));
        for (String reason : REASON_LABELS) {
            assertTrue("hard overflow missing reason " + reason, content.contains(reason));
        }
        assertEquals(0, tokenCount(content, PENDING_SIGNAL_TOKEN));
        assertPendingTokensAreAtomic(producer, content, "hard-overflow");
        assertExactOmissionCount(producer, content, "hard-overflow");
    }

    private static void assertSnapshotInvariants(
            String producer, String content, String rendered, String context) {
        assertTrue(context,
                rendered.length() <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);
        assertTrue(context + " missing stats section", rendered.contains(STATS_PREFIX));
        assertTrue(context + " missing release section", rendered.contains("release="));
        assertTrue(context + " missing history section", rendered.contains("hist="));
        assertTrue(context + " missing flap section", rendered.contains("flap×"));
        assertTrue(context + " missing latency section", rendered.contains("lat"));
        assertTrue(context + " missing latency guard tail", rendered.contains("guard="));
        assertPendingTokensAreAtomic(producer, content, context);
        assertExactOmissionCount(producer, content, context);
    }

    private static RouteDecisionDiagnosticsSnapshot pressuredSnapshot(
            String producer, int pressureLength) {
        String pressure = "x".repeat(pressureLength);
        return new RouteDecisionDiagnosticsSnapshot(
                "d" + pressure, 0, 0,
                true, "r" + pressure, "pending", 0, 1,
                producer,
                "h" + pressure, 0,
                1, 0L,
                true, Long.MIN_VALUE, Long.MAX_VALUE,
                0L, 0L);
    }

    private static String statsContent(String rendered) {
        int start = rendered.indexOf(STATS_PREFIX);
        assertTrue("stats section missing: " + rendered, start >= 0);
        int contentStart = start + STATS_PREFIX.length();
        int end = rendered.indexOf('}', contentStart);
        assertTrue("stats section not closed: " + rendered, end >= 0);
        return rendered.substring(contentStart, end);
    }

    private static void assertPendingTokensAreAtomic(
            String producer, String rendered, String context) {
        for (String token : tokens(rendered)) {
            if (token.indexOf('>') < 0 && token.indexOf('×') < 0) continue;
            assertTrue(
                    context + " contains a pending-token fragment: " + token,
                    containsToken(producer, token));
        }
    }

    private static void assertExactOmissionCount(
            String producer, String rendered, String context) {
        String[] sourceTokens = tokens(producer);
        String[] renderedTokens = tokens(rendered);
        int omitted = -1;
        int visible = 0;
        for (String token : renderedTokens) {
            if (token.startsWith(OMISSION_PREFIX)) {
                omitted = Integer.parseInt(token.substring(OMISSION_PREFIX.length()));
            } else {
                visible++;
            }
        }

        if (omitted < 0) {
            assertEquals(context + " token count without omission marker",
                    sourceTokens.length, visible);
        } else {
            assertEquals(
                    context + " omission marker does not match hidden token count",
                    sourceTokens.length - visible,
                    omitted);
        }
    }

    private static int tokenCount(String value, String expected) {
        int count = 0;
        for (String token : tokens(value)) {
            if (expected.equals(token)) count++;
        }
        return count;
    }

    private static boolean containsToken(String value, String expected) {
        for (String token : tokens(value)) {
            if (expected.equals(token)) return true;
        }
        return false;
    }

    private static String[] tokens(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
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
}
