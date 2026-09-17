package com.livecopilot.micprobe;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsSnapshotAdversarialTest {
    private static final String OMISSION_PREFIX = "…more+";

    @Test
    public void shortStatsNormalizeAsciiAndUnicodeWhitespaceBeforeBudgetCheck() {
        String source = "  rel\ts/r/x\nrtslow\u20031/2/3\u00a0sig=risky  ";

        assertEquals(
                "rel s/r/x rtslow 1/2/3 sig=risky",
                boundedStatsContent(source, RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET));
    }

    @Test
    public void whitespaceOnlyBreakdownDoesNotCreateStatsSection() {
        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                "learning", 1, 1,
                false, "-", "-", 0, 3,
                " \t\n\u2003\u00a0 ",
                "-", 0,
                0, 0L,
                false, Long.MIN_VALUE, Long.MAX_VALUE, 0L, 0L);

        assertEquals("route why=learning n=1/1", snapshot.format());
    }

    @Test
    public void unicodeWhitespaceCompactionUsesOneTokenizerForExactOmissionCount() {
        String source = joinWithWhitespace(
                new String[]{
                        "rel", "s/r/x",
                        "rtslow", "1/1/0", "sig=a", "rev10%@8",
                        "rtfast", "2/2/0", "sig=b", "rev20%@8",
                        "ffast", "3/3/0", "sig=c", "rev30%@8",
                        "fslow", "4/4/0", "sig=d", "rev40%@8",
                        "tail=1", "tail=2"
                });

        for (int budget = 34;
                budget <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET;
                budget++) {
            String first = boundedStatsContent(source, budget);
            String second = boundedStatsContent(source, budget);
            String context = "budget=" + budget;

            assertEquals(context, first, second);
            assertTrue(context, first.length() <= budget);
            assertFalse(context, containsDiagnosticWhitespace(first));
            assertOmissionCountMatches(source, first, context);
        }
    }

    @Test
    public void unknownReasonGroupDoesNotLeakItsSignalIntoKnownReasonCoverage() {
        String source = "rel s/r/x"
                + " rtslow 1/1/0 sig=a"
                + " future 9/9/9 sig=z"
                + " rtfast 2/2/0 sig=b"
                + " ffast 3/3/0 sig=c"
                + " fslow 4/4/0 sig=d";

        String rendered = boundedStatsContent(
                source, RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET);

        assertEquals(
                "rel s/r/x rtslow 1/1/0 sig=a rtfast 2/2/0 sig=b"
                        + " ffast 3/3/0 sig=c fslow 4/4/0 sig=d …more+3",
                rendered);
        assertFalse(rendered.contains("future"));
        assertFalse(rendered.contains("sig=z"));
        assertOmissionCountMatches(source, rendered, "unknown reason boundary");
    }

    @Test
    public void duplicateKnownReasonsStayDeterministicAndCompactIdentityIsUnique() {
        String source = "rel s/r/x"
                + " rtslow 1/1/0 sig=a"
                + " rtslow 9/9/9 sig=duplicate"
                + " rtfast 2/2/0 sig=b"
                + " ffast 3/3/0 sig=c"
                + " fslow 4/4/0 sig=d";

        for (int budget = 40;
                budget <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET;
                budget++) {
            String rendered = boundedStatsContent(source, budget);
            String context = "duplicate budget=" + budget;

            assertTrue(context, rendered.length() <= budget);
            assertEquals(context, rendered, boundedStatsContent(source, budget));
            assertTrue(context, tokenCount(rendered, "rtslow") <= 1);
            assertOmissionCountMatches(source, rendered, context);
        }
    }

    @Test
    public void malformedTokensRemainDeterministicAndBudgetBounded() {
        String huge = "x".repeat(180);
        String[] sources = {
                "rel s/r/x rtslow sig=a rtfast nope sig=b"
                        + " ffast 3/3/0 sig=c fslow 4/4/0 sig=d extra=1",
                "rel s/r/x rtslow /// sig=a rtfast 2/2/0 sig=b"
                        + " ffast 3/3/0 sig=c fslow 4/4/0 sig=d",
                "rel s/r/x rtslow 1/1/0 sig= rtfast 2/2/0 rev%"
                        + " ffast 3/3/0 conf fslow 4/4/0 rconf",
                "rel s/r/x rtslow 1/1/0 " + huge
                        + " rtfast 2/2/0 sig=b ffast 3/3/0 sig=c fslow 4/4/0 sig=d",
                "rel\u2003s/r/x\trtslow\u00a01/1/0\nsig=a"
                        + "\u2003rtfast\u00a02/2/0\tsig=b"
                        + "\nffast 3/3/0 sig=c\u2003fslow 4/4/0 sig=d"
        };

        for (int sourceIndex = 0; sourceIndex < sources.length; sourceIndex++) {
            String source = sources[sourceIndex];
            for (int budget = 34;
                    budget <= RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET;
                    budget++) {
                String first = boundedStatsContent(source, budget);
                String second = boundedStatsContent(source, budget);
                String context = "source=" + sourceIndex + " budget=" + budget;

                assertEquals(context, first, second);
                assertTrue(context, first.length() <= budget);
                assertOmissionCountMatches(source, first, context);
            }
        }
    }

    @Test
    public void adversarialFullSnapshotStillKeepsHardLimitAndGuardTail() {
        String source = "rel\u2003s/r/x"
                + "\trtslow\u00a01/1/0 sig=a"
                + "\nfuture 9/9/9 sig=z"
                + "\u2003rtfast 2/2/0 sig=b"
                + "\trtslow 8/8/8 sig=duplicate"
                + "\nffast 3/3/0 sig=c"
                + "\u00a0fslow 4/4/0 sig=d malformed=";
        String longLabel = "adversarial-" + "x".repeat(320);

        RouteDecisionDiagnosticsSnapshot snapshot = new RouteDecisionDiagnosticsSnapshot(
                longLabel, Integer.MAX_VALUE, Integer.MAX_VALUE,
                true, longLabel, "pending", Integer.MAX_VALUE, Integer.MAX_VALUE,
                source,
                longLabel, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Long.MAX_VALUE,
                true, Long.MAX_VALUE - 1L, Long.MAX_VALUE - 2L,
                Long.MAX_VALUE, Long.MAX_VALUE);

        String first = snapshot.format();
        String second = snapshot.format();

        assertEquals(first, second);
        assertTrue(first.length() <= RouteDecisionDiagnosticsSnapshot.SNAPSHOT_CHAR_BUDGET);
        assertTrue(first.contains(" • stats{"));
        assertTrue(first.contains("rtslow"));
        assertTrue(first.contains("rtfast"));
        assertTrue(first.contains("ffast"));
        assertTrue(first.contains("fslow"));
        assertTrue(first.contains("guard="));
        assertFalse(first.contains("sig=z"));
        assertFalse(first.contains("\t"));
        assertFalse(first.contains("\n"));
        assertFalse(first.contains("\u2003"));
        assertFalse(first.contains("\u00a0"));
    }

    @Test
    public void canonicalProducerSpacingRemainsByteForByteStable() {
        String source = "rel s/r/x rtslow 1/1/0 sup=1 sig=learn";
        assertEquals(
                source,
                boundedStatsContent(source, RouteDecisionDiagnosticsSnapshot.RELEASE_STATS_CHAR_BUDGET));
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
            assertEquals(
                    context + " token count without omission marker",
                    sourceTokens.length,
                    renderedTokens.length);
            return;
        }

        int visibleSourceTokens = renderedTokens.length - 1;
        assertEquals(
                context + " omission marker does not match hidden token count",
                sourceTokens.length - visibleSourceTokens,
                omitted);
    }

    private static String joinWithWhitespace(String[] tokens) {
        char[] separators = {' ', '\t', '\n', '\u2003', '\u00a0'};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) out.append(separators[(i - 1) % separators.length]);
            out.append(tokens[i]);
        }
        return out.toString();
    }

    private static boolean containsDiagnosticWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != ' ' && isDiagnosticWhitespace(c)) return true;
        }
        return false;
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
        int count = 0;
        boolean inToken = false;
        for (int i = 0; i < value.length(); i++) {
            boolean whitespace = isDiagnosticWhitespace(value.charAt(i));
            if (!whitespace && !inToken) {
                count++;
                inToken = true;
            } else if (whitespace) {
                inToken = false;
            }
        }

        String[] tokens = new String[count];
        int tokenIndex = 0;
        int start = -1;
        for (int i = 0; i <= value.length(); i++) {
            boolean boundary = i == value.length()
                    || isDiagnosticWhitespace(value.charAt(i));
            if (!boundary && start < 0) {
                start = i;
            } else if (boundary && start >= 0) {
                tokens[tokenIndex++] = value.substring(start, i);
                start = -1;
            }
        }
        return tokens;
    }

    private static boolean isDiagnosticWhitespace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }
}
