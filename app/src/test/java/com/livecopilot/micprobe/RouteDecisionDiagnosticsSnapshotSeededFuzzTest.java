package com.livecopilot.micprobe;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsSnapshotSeededFuzzTest {
    private static final long SEED = 0x1185EEDL;
    private static final int CASE_COUNT = 384;
    private static final int MIN_VALID_STATS_BUDGET = 34;
    private static final String OMISSION_PREFIX = "…more+";
    private static final String[] KNOWN_REASONS = {
            "rtslow", "rtfast", "ffast", "fslow"
    };
    private static final char[] DIAGNOSTIC_SEPARATORS = {
            ' ', '\t', '\n', '\r', '\u2003', '\u00a0', '\u202f', '\u205f'
    };

    @Test
    public void seededWhitespaceAndMalformedFuzzPreservesCompactInvariants() {
        Random random = new Random(SEED);

        for (int caseIndex = 0; caseIndex < CASE_COUNT; caseIndex++) {
            List<String> tokens = generatedTokens(random, caseIndex);
            String canonical = String.join(" ", tokens);
            String fuzzedWhitespace = joinWithRandomWhitespace(tokens, random);

            for (int budget = MIN_VALID_STATS_BUDGET;
                    budget <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET;
                    budget++) {
                String context = context(caseIndex, budget, canonical);
                String canonicalFirst = boundedStatsContent(canonical, budget);
                String canonicalSecond = boundedStatsContent(canonical, budget);
                String fuzzed = boundedStatsContent(fuzzedWhitespace, budget);

                assertEquals(context + " repeated formatting diverged",
                        canonicalFirst, canonicalSecond);
                assertEquals(context + " whitespace-equivalent source diverged",
                        canonicalFirst, fuzzed);
                assertTrue(context + " exceeded content budget",
                        canonicalFirst.length() <= budget);
                assertFalse(context + " leaked non-canonical diagnostic whitespace",
                        containsNonCanonicalWhitespace(canonicalFirst));
                assertFalse(context + " emitted repeated spaces",
                        canonicalFirst.contains("  "));
                assertOmissionCountMatches(
                        tokens.size(), canonicalFirst, context);
            }
        }
    }

    @Test
    public void seededFullSnapshotFuzzPreservesHardLimitStatsAccountingAndGuardTail() {
        Random random = new Random(SEED ^ 0x224096L);

        for (int caseIndex = 0; caseIndex < CASE_COUNT; caseIndex++) {
            List<String> tokens = generatedTokens(random, caseIndex);
            String source = joinWithRandomWhitespace(tokens, random);
            int labelLength = random.nextInt(520);
            String label = "fuzz-" + caseIndex + '-' + "x".repeat(labelLength);

            int samples = caseIndex % 5 == 0 ? Integer.MAX_VALUE : random.nextInt(32);
            int streak = caseIndex % 7 == 0 ? Integer.MAX_VALUE : random.nextInt(12);
            long margin = caseIndex % 9 == 0 ? Long.MAX_VALUE : random.nextInt(10_000);

            RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                    label, samples, samples,
                    true, label, "pending", streak, Math.max(1, random.nextInt(8)),
                    source,
                    label, streak,
                    Math.max(1, random.nextInt(8)), margin,
                    true,
                    caseIndex % 3 == 0 ? Long.MAX_VALUE - 1L : random.nextInt(20_000) - 10_000L,
                    caseIndex % 4 == 0 ? Long.MAX_VALUE - 2L : random.nextInt(20_000),
                    caseIndex % 2 == 0 ? Long.MAX_VALUE : random.nextInt(5_000),
                    caseIndex % 6 == 0 ? Long.MAX_VALUE : random.nextInt(5_000));

            String first = snapshot.format();
            String second = snapshot.format();
            String context = "seed=" + SEED + " fullCase=" + caseIndex
                    + " labelLength=" + labelLength;

            assertEquals(context + " repeated snapshot formatting diverged", first, second);
            assertTrue(context + " exceeded hard snapshot budget",
                    first.length() <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);
            assertTrue(context + " lost stats section", first.contains(" • stats{"));
            assertTrue(context + " lost latency guard tail", first.contains("guard="));

            String stats = statsContent(first);
            assertFalse(context + " leaked non-canonical diagnostic whitespace",
                    containsNonCanonicalWhitespace(stats));
            assertFalse(context + " emitted repeated spaces in stats",
                    stats.contains("  "));
            assertTrue(context + " exceeded stats content budget",
                    stats.length() <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET);
            assertOmissionCountMatches(tokens.size(), stats, context);
        }
    }

    private static List<String> generatedTokens(Random random, int caseIndex) {
        List<String> tokens = new ArrayList<>();
        if (random.nextInt(5) != 0) {
            tokens.add("rel");
            tokens.add("s/r/x");
        } else if (random.nextBoolean()) {
            tokens.add("rel");
        } else {
            tokens.add("header-" + caseIndex);
        }

        int groupCount = 2 + random.nextInt(9);
        for (int group = 0; group < groupCount; group++) {
            int groupKind = random.nextInt(10);
            String reason;
            if (groupKind < 6) {
                reason = KNOWN_REASONS[random.nextInt(KNOWN_REASONS.length)];
            } else if (groupKind < 9) {
                reason = "future" + (caseIndex % 11) + '_' + group;
            } else {
                reason = "malformed-group-" + group;
            }
            tokens.add(reason);

            int countKind = random.nextInt(6);
            if (countKind < 4) {
                tokens.add(validCountTriple(random));
            } else if (countKind == 4) {
                tokens.add(malformedCount(random));
            }

            int detailCount = 1 + random.nextInt(5);
            for (int detail = 0; detail < detailCount; detail++) {
                tokens.add(detailToken(random, caseIndex, group, detail));
            }

            if (random.nextInt(5) == 0) {
                tokens.add("orphan=" + random.nextInt(100));
            }
        }

        if (random.nextInt(3) == 0) {
            tokens.add("tail=" + "z".repeat(1 + random.nextInt(180)));
        }
        return tokens;
    }

    private static String validCountTriple(Random random) {
        return random.nextInt(10) + "/" + random.nextInt(10) + "/" + random.nextInt(10);
    }

    private static String malformedCount(Random random) {
        switch (random.nextInt(5)) {
            case 0:
                return "///";
            case 1:
                return random.nextInt(10) + "/" + random.nextInt(10);
            case 2:
                return "x/y/z";
            case 3:
                return random.nextInt(10) + "//" + random.nextInt(10);
            default:
                return "count=" + random.nextInt(10);
        }
    }

    private static String detailToken(
            Random random, int caseIndex, int group, int detail) {
        switch (random.nextInt(10)) {
            case 0:
                return "sig=s" + random.nextInt(8);
            case 1:
                return "sig=" + "v".repeat(1 + random.nextInt(120));
            case 2:
                return "rev" + random.nextInt(101) + "%@" + (1 + random.nextInt(12));
            case 3:
                return "conf" + random.nextInt(101) + "%@" + (1 + random.nextInt(12))
                        + "/" + (random.nextBoolean() ? "strong" : "weak");
            case 4:
                return "rconf" + random.nextInt(101) + "%@" + (1 + random.nextInt(12));
            case 5:
                return "sup=" + random.nextInt(100);
            case 6:
                return "cancel=" + random.nextInt(10) + '/' + random.nextInt(10);
            case 7:
                return "tr=" + random.nextInt(10) + '/' + random.nextInt(10);
            case 8:
                return "malformed=" + caseIndex + '_' + group + '_' + detail;
            default:
                return "blob=" + "b".repeat(1 + random.nextInt(180));
        }
    }

    private static String joinWithRandomWhitespace(List<String> tokens, Random random) {
        StringBuilder out = new StringBuilder();
        appendRandomWhitespace(out, random, random.nextInt(3));
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                appendRandomWhitespace(out, random, 1 + random.nextInt(3));
            }
            out.append(tokens.get(i));
        }
        appendRandomWhitespace(out, random, random.nextInt(3));
        return out.toString();
    }

    private static void appendRandomWhitespace(StringBuilder out, Random random, int count) {
        for (int i = 0; i < count; i++) {
            out.append(DIAGNOSTIC_SEPARATORS[random.nextInt(DIAGNOSTIC_SEPARATORS.length)]);
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
            int sourceTokenCount, String rendered, String context) {
        String[] renderedTokens = tokens(rendered);
        int markerCount = 0;
        int omitted = -1;
        for (String token : renderedTokens) {
            if (token.startsWith(OMISSION_PREFIX)) {
                markerCount++;
                omitted = Integer.parseInt(token.substring(OMISSION_PREFIX.length()));
            }
        }

        assertTrue(context + " emitted multiple omission markers", markerCount <= 1);
        if (markerCount == 0) {
            assertEquals(context + " token count changed without omission marker",
                    sourceTokenCount, renderedTokens.length);
            return;
        }

        int visibleSourceTokens = renderedTokens.length - 1;
        assertEquals(context + " omission marker does not match hidden source tokens",
                sourceTokenCount - visibleSourceTokens, omitted);
        assertTrue(context + " omission marker must hide at least one token", omitted > 0);
    }

    private static String statsContent(String formatted) {
        int start = formatted.indexOf("stats{") + "stats{".length();
        return formatted.substring(start, formatted.indexOf('}', start));
    }

    private static String[] tokens(String value) {
        if (value == null || value.isEmpty()) return new String[0];
        return value.split(" ");
    }

    private static boolean containsNonCanonicalWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != ' ' && (Character.isWhitespace(c) || Character.isSpaceChar(c))) {
                return true;
            }
        }
        return false;
    }

    private static String context(int caseIndex, int budget, String source) {
        return "seed=" + SEED + " case=" + caseIndex + " budget=" + budget
                + " source=" + source;
    }
}
