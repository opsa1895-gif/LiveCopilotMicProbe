package com.livecopilot.micprobe;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsSnapshotHardeningTest {
    private static final int MIN_VALID_STATS_BUDGET = 34;
    private static final int THREE_SIGNAL_BUDGET = 52;
    private static final String OMISSION_PREFIX = "…more+";
    private static final String[] REASON_LABELS = {
            "rtslow", "rtfast", "ffast", "fslow"
    };
    private static final String[] REASON_GROUPS = {
            "rtslow 1/1/0 sig=a rev10%@8 conf75%@8/strong",
            "rtfast 2/2/0 sig=b rev20%@8 conf75%@8/strong",
            "ffast 3/3/0 sig=c rev30%@8 conf75%@8/strong",
            "fslow 4/4/0 sig=d rev40%@8 conf75%@8/strong"
    };

    @Test
    public void everyReasonPermutationIsDeterministicMonotonicAndBudgetBounded() {
        List<int[]> permutations = permutations();
        assertEquals(24, permutations.size());

        for (int[] order : permutations) {
            String breakdown = breakdown(order);
            int previousSignalCoverage = -1;

            for (int budget = MIN_VALID_STATS_BUDGET;
                    budget <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET;
                    budget++) {
                String first = boundedStatsContent(breakdown, budget);
                String second = boundedStatsContent(breakdown, budget);

                assertEquals(matrixContext(order, budget), first, second);
                assertTrue(matrixContext(order, budget), first.length() <= budget);
                assertOmissionCountMatches(breakdown, first, matrixContext(order, budget));

                int signalCoverage = tokenPrefixCount(first, "sig=");
                assertTrue(
                        matrixContext(order, budget)
                                + " signal coverage regressed from "
                                + previousSignalCoverage + " to " + signalCoverage,
                        signalCoverage >= previousSignalCoverage);
                previousSignalCoverage = signalCoverage;

                if (signalCoverage < REASON_LABELS.length) {
                    assertFalse(
                            matrixContext(order, budget)
                                    + " exposed lower-priority reversal detail before all signals",
                            containsTokenPrefix(first, "rev"));
                }
            }
        }
    }

    @Test
    public void equalSizeAdaptiveTieBreakSelectsSameReasonSetAcrossPermutations() {
        for (int[] order : permutations()) {
            String rendered = boundedStatsContent(breakdown(order), THREE_SIGNAL_BUDGET);
            String context = matrixContext(order, THREE_SIGNAL_BUDGET);

            assertEquals(context, 3, tokenPrefixCount(rendered, "sig="));
            assertTrue(context, containsToken(rendered, "sig=a"));
            assertTrue(context, containsToken(rendered, "sig=b"));
            assertTrue(context, containsToken(rendered, "sig=c"));
            assertFalse(context, containsToken(rendered, "sig=d"));
            assertOmissionCountMatches(breakdown(order), rendered, context);
        }
    }

    @Test
    public void fullSnapshotPermutationAndOverflowMatrixAlwaysKeepsHardLimitAndGuardTail() {
        int[] labelLengths = {0, 8, 32, 96, 256, 512};

        for (int[] order : permutations()) {
            String breakdown = breakdown(order);
            for (int labelLength : labelLengths) {
                String label = "label-" + "x".repeat(labelLength);
                RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                        label, Integer.MAX_VALUE, Integer.MAX_VALUE,
                        true, label, "pending", Integer.MAX_VALUE, Integer.MAX_VALUE,
                        breakdown,
                        label, Integer.MAX_VALUE,
                        Integer.MAX_VALUE, Long.MAX_VALUE,
                        true, Long.MAX_VALUE - 1L, Long.MAX_VALUE - 2L,
                        Long.MAX_VALUE, Long.MAX_VALUE);

                String first = snapshot.format();
                String second = snapshot.format();
                String context = "order=" + orderText(order) + " labelLength=" + labelLength;

                assertEquals(context, first, second);
                assertTrue(context, first.length() <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);
                assertTrue(context, first.contains(" • stats{"));
                for (String reason : REASON_LABELS) {
                    assertTrue(context + " missing reason " + reason, first.contains(reason));
                }
                assertTrue(context + " missing latency guard tail", first.contains("guard="));
            }
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

    private static void assertOmissionCountMatches(
            String source, String rendered, String context) {
        String[] sourceTokens = tokens(source);
        String[] renderedTokens = tokens(rendered);
        int markerIndex = -1;
        int omitted = -1;
        for (int i = 0; i < renderedTokens.length; i++) {
            if (renderedTokens[i].startsWith(OMISSION_PREFIX)) {
                markerIndex = i;
                omitted = Integer.parseInt(renderedTokens[i].substring(OMISSION_PREFIX.length()));
                break;
            }
        }

        if (markerIndex < 0) {
            assertEquals(context + " token count without omission marker",
                    sourceTokens.length, renderedTokens.length);
            return;
        }

        int visibleSourceTokens = renderedTokens.length - 1;
        assertEquals(
                context + " omission marker does not match hidden token count",
                sourceTokens.length - visibleSourceTokens,
                omitted);
    }

    private static int tokenPrefixCount(String value, String prefix) {
        int count = 0;
        for (String token : tokens(value)) {
            if (token.startsWith(prefix)) count++;
        }
        return count;
    }

    private static boolean containsTokenPrefix(String value, String prefix) {
        return tokenPrefixCount(value, prefix) > 0;
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

    private static String breakdown(int[] order) {
        StringBuilder out = new StringBuilder("rel s/r/x");
        for (int index : order) {
            out.append(' ').append(REASON_GROUPS[index]);
        }
        return out.toString();
    }

    private static List<int[]> permutations() {
        List<int[]> out = new ArrayList<>();
        permute(new int[]{0, 1, 2, 3}, 0, out);
        return out;
    }

    private static void permute(int[] values, int index, List<int[]> out) {
        if (index == values.length) {
            out.add(values.clone());
            return;
        }
        for (int i = index; i < values.length; i++) {
            int swap = values[index];
            values[index] = values[i];
            values[i] = swap;
            permute(values, index + 1, out);
            swap = values[index];
            values[index] = values[i];
            values[i] = swap;
        }
    }

    private static String matrixContext(int[] order, int budget) {
        return "order=" + orderText(order) + " budget=" + budget;
    }

    private static String orderText(int[] order) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < order.length; i++) {
            if (i > 0) out.append('>');
            out.append(REASON_LABELS[order[i]]);
        }
        return out.toString();
    }
}
