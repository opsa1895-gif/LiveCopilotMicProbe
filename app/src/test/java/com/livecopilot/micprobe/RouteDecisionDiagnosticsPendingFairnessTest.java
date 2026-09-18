package com.livecopilot.micprobe;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsPendingFairnessTest {
    private static final int MIN_VALID_STATS_BUDGET = 34;
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

    @Test
    public void fourPendingSignalsMaximizeCoverageMonotonicallyAcrossEveryBudget() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (String reason : RELEASE_REASONS) {
            recordDirectionalSequence(stats, reason, PENDING_SIGNAL_SEQUENCE);
        }
        String producer = stats.diagnostics();

        assertEquals(4, tokenCount(producer, PENDING_SIGNAL_TOKEN));

        int previousCoverage = -1;
        for (int budget = MIN_VALID_STATS_BUDGET;
                budget <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET;
                budget++) {
            String rendered = boundedStatsContent(producer, budget);
            String context = "budget=" + budget + " rendered=" + rendered;

            assertTrue(context, rendered.length() <= budget);
            assertPendingTokensAreAtomic(producer, rendered, context);
            assertExactOmissionCount(producer, rendered, context);

            int coverage = tokenCount(rendered, PENDING_SIGNAL_TOKEN);
            assertTrue(
                    context + " pending-signal coverage regressed from "
                            + previousCoverage + " to " + coverage,
                    coverage >= previousCoverage);
            assertEquals(
                    context + " did not maximize the number of pending signal reasons",
                    theoreticalPendingSignalCoverage(producer, budget),
                    coverage);
            previousCoverage = coverage;
        }

        assertEquals(
                2,
                tokenCount(
                        boundedStatsContent(
                                producer,
                                RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET),
                        PENDING_SIGNAL_TOKEN));
    }

    @Test
    public void everyPendingReliabilityReasonPairGetsOneAtomicConfAtFullBudget() {
        for (int first = 0; first < RELEASE_REASONS.length; first++) {
            for (int second = first + 1; second < RELEASE_REASONS.length; second++) {
                RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
                recordDirectionalSequence(
                        stats, RELEASE_REASONS[first], PENDING_RELIABILITY_SEQUENCE);
                recordDirectionalSequence(
                        stats, RELEASE_REASONS[second], PENDING_RELIABILITY_SEQUENCE);

                String producer = stats.diagnostics();
                String rendered = boundedStatsContent(
                        producer,
                        RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET);
                String context = "pair=" + first + "/" + second + " rendered=" + rendered;

                assertEquals(context, 2, tokenCount(producer, PENDING_CONF_TOKEN));
                assertEquals(context, 1, tokenCount(rendered, PENDING_CONF_TOKEN));
                assertEquals(context, 2, tokenPrefixCount(rendered, "sig="));
                assertEquals(context, 2, tokenPrefixCount(rendered, "rev"));
                assertTrue(
                        context,
                        rendered.length()
                                <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET);
                assertPendingTokensAreAtomic(producer, rendered, context);
                assertExactOmissionCount(producer, rendered, context);
            }
        }
    }

    @Test
    public void pendingReliabilityPairCoverageNeverRegressesAsBudgetGrows() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        recordDirectionalSequence(stats, "rt-slowdown", PENDING_RELIABILITY_SEQUENCE);
        recordDirectionalSequence(stats, "file-speedup", PENDING_RELIABILITY_SEQUENCE);
        String producer = stats.diagnostics();

        int previousCoverage = -1;
        for (int budget = MIN_VALID_STATS_BUDGET;
                budget <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET;
                budget++) {
            String rendered = boundedStatsContent(producer, budget);
            String context = "budget=" + budget + " rendered=" + rendered;

            int coverage = tokenCount(rendered, PENDING_CONF_TOKEN);
            assertTrue(
                    context + " pending-conf coverage regressed from "
                            + previousCoverage + " to " + coverage,
                    coverage >= previousCoverage);
            assertTrue(context, coverage <= 1);
            assertPendingTokensAreAtomic(producer, rendered, context);
            assertExactOmissionCount(producer, rendered, context);
            previousCoverage = coverage;
        }
        assertEquals(1, previousCoverage);
    }

    @Test
    public void mixedPendingSignalAndReliabilityReasonsKeepEveryFirstPrioritySignal() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        recordDirectionalSequence(stats, "rt-slowdown", PENDING_SIGNAL_SEQUENCE);
        recordDirectionalSequence(stats, "rt-speedup", PENDING_SIGNAL_SEQUENCE);
        recordDirectionalSequence(stats, "file-speedup", PENDING_RELIABILITY_SEQUENCE);
        recordDirectionalSequence(stats, "file-slowdown", PENDING_RELIABILITY_SEQUENCE);

        String producer = stats.diagnostics();
        String rendered = boundedStatsContent(
                producer,
                RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET);
        String context = "rendered=" + rendered;

        assertEquals(context, 4, tokenPrefixCount(rendered, "sig="));
        assertEquals(context, 2, tokenCount(rendered, PENDING_SIGNAL_TOKEN));
        assertPendingTokensAreAtomic(producer, rendered, context);
        assertExactOmissionCount(producer, rendered, context);
    }

    private static int theoreticalPendingSignalCoverage(String producer, int budget) {
        String[] sourceTokens = tokens(producer);
        int totalTokens = sourceTokens.length;
        int best = 0;

        for (int mask = 0; mask < (1 << REASON_LABELS.length); mask++) {
            StringBuilder out = new StringBuilder();
            int visible = 0;
            for (String reason : REASON_LABELS) {
                appendToken(out, reason);
                visible++;
            }

            int selectedPending = 0;
            for (int reason = 0; reason < REASON_LABELS.length; reason++) {
                if ((mask & (1 << reason)) == 0) continue;
                appendToken(out, PENDING_SIGNAL_TOKEN);
                visible++;
                selectedPending++;
            }

            int omitted = totalTokens - visible;
            if (omitted > 0) {
                appendToken(out, OMISSION_PREFIX + omitted);
            }
            if (out.length() <= budget) {
                best = Math.max(best, selectedPending);
            }
        }
        return best;
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
            assertEquals(context, sourceTokens.length, visible);
        } else {
            assertEquals(context, sourceTokens.length - visible, omitted);
        }
    }

    private static String boundedStatsContent(String value, int budget) {
        try {
            Method method = RouteDecisionDiagnosticsSnapshot.class.getDeclaredMethod(
                    "boundedStatsContent", String.class, int.class);
            method.setAccessible(true);
            return (String) method.invoke(null, value, budget);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Unable to invoke boundedStatsContent", error);
        }
    }

    private static int tokenPrefixCount(String value, String prefix) {
        int count = 0;
        for (String token : tokens(value)) {
            if (token.startsWith(prefix)) count++;
        }
        return count;
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

    private static void appendToken(StringBuilder out, String token) {
        if (out.length() > 0) out.append(' ');
        out.append(token);
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
