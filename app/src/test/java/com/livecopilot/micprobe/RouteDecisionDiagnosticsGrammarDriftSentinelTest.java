package com.livecopilot.micprobe;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteDecisionDiagnosticsGrammarDriftSentinelTest {
    private static final String[] EXPECTED_PRIORITY_PREFIXES = {
            "sig=", "rev", "conf", "rconf"
    };
    private static final Set<String> PRIORITY_FAMILIES = Set.of(
            "sig", "rev", "conf", "rconf");
    private static final Set<String> SECONDARY_FAMILIES = Set.of(
            "sup", "tr", "cancel", "rtr", "rcancel", "rmat");
    private static final String[] EXPECTED_RELEASE_REASONS = {
            "rt-slowdown", "rt-speedup", "file-speedup", "file-slowdown"
    };
    private static final String[] EXPECTED_REASON_LABELS = {
            "rtslow", "rtfast", "ffast", "fslow"
    };
    private static final String[] EXPECTED_OUTCOMES = {
            "stable", "reversal", "expired", "superseded"
    };
    private static final Set<String> REASON_LABELS = Set.of(
            "rtslow", "rtfast", "ffast", "fslow");
    private static final String RICH_GRAMMAR_SEQUENCE = "srsrsrsrssrsssr";

    private static final Pattern DIRECT_DETAIL_APPEND = Pattern.compile(
            "\\.append\\(\" ([A-Za-z][A-Za-z0-9]*)(?:=)?");
    private static final Pattern SPACED_DETAIL_APPEND = Pattern.compile(
            "\\.append\\(' '\\)\\s*\\.append\\(\"([A-Za-z][A-Za-z0-9]*)(?:=)?");
    private static final Pattern REASON_INPUT_MAPPING = Pattern.compile(
            "if \\(\"([^\"]+)\"\\.equals\\(reason\\)\\) return REASON_[A-Z_]+;");
    private static final Pattern REASON_LABEL_MAPPING = Pattern.compile(
            "if \\(reason == REASON_[A-Z_]+\\) return \"([^\"]+)\";");
    private static final Pattern OUTCOME_INPUT_MAPPING = Pattern.compile(
            "if \\(\"([^\"]+)\"\\.equals\\(outcome\\)\\) return OUTCOME_[A-Z_]+;");

    @Test
    public void producerDetailFamiliesRequireExplicitClosedWorldClassification() throws Exception {
        Set<String> classified = classifiedFamilies();
        Set<String> discovered = producerDetailFamiliesFromSource();

        assertEquals(
                "RouteReleaseOutcomeStats.diagnostics() grammar changed. "
                        + "Classify every new detail family explicitly as priority or secondary "
                        + "and add a real producer fixture before updating this sentinel.",
                classified,
                discovered);
    }

    @Test
    public void formatterPriorityPrefixesStayExplicitAndOrdered() throws Exception {
        Field field = RouteDecisionDiagnosticsSnapshot.class
                .getDeclaredField("STATS_DETAIL_PRIORITY_PREFIXES");
        field.setAccessible(true);
        String[] actual = (String[]) field.get(null);

        assertArrayEquals(EXPECTED_PRIORITY_PREFIXES, actual);

        Set<String> normalized = new TreeSet<>();
        for (String prefix : actual) {
            normalized.add(normalizeFamily(prefix));
        }
        assertEquals(new TreeSet<>(PRIORITY_FAMILIES), normalized);
    }

    @Test
    public void realRichProducerFixtureEmitsEveryClassifiedDetailFamily() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        recordDirectionalSequence(stats, "rt-slowdown", RICH_GRAMMAR_SEQUENCE);
        stats.record("rt-slowdown", "superseded");

        Set<String> emitted = emittedDetailFamilies(stats.diagnostics());

        assertEquals(
                "The closed-world grammar inventory must be backed by a real producer state. "
                        + "Extend the rich integration fixture when adding a detail family.",
                classifiedFamilies(),
                emitted);
    }

    @Test
    public void producerReleaseReasonInputsStayClosedWorldAndOrdered() throws Exception {
        assertArrayEquals(
                EXPECTED_RELEASE_REASONS,
                mappedValuesFromProducerMethod("private static int reasonIndex(String reason) {",
                        REASON_INPUT_MAPPING));
    }

    @Test
    public void producerReasonLabelsStayClosedWorldAndOrdered() throws Exception {
        assertArrayEquals(
                EXPECTED_REASON_LABELS,
                mappedValuesFromProducerMethod("private static String reasonLabel(int reason) {",
                        REASON_LABEL_MAPPING));
    }

    @Test
    public void producerOutcomeInputsStayClosedWorldAndOrdered() throws Exception {
        assertArrayEquals(
                EXPECTED_OUTCOMES,
                mappedValuesFromProducerMethod("private static int outcomeIndex(String outcome) {",
                        OUTCOME_INPUT_MAPPING));
    }

    @Test
    public void formatterReasonLabelsStayExplicitAndOrdered() throws Exception {
        Field field = RouteDecisionDiagnosticsSnapshot.class
                .getDeclaredField("STATS_REASON_LABELS");
        field.setAccessible(true);
        String[] actual = (String[]) field.get(null);

        assertArrayEquals(EXPECTED_REASON_LABELS, actual);
    }

    @Test
    public void producerHeaderAndCountTripleStayStableAcrossEveryReason() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        for (String reason : EXPECTED_RELEASE_REASONS) {
            record(stats, reason, "stable", 2);
            record(stats, reason, "reversal", 3);
            record(stats, reason, "expired", 4);
            record(stats, reason, "superseded", 5);
        }

        String diagnostics = stats.diagnostics();
        String[] tokens = diagnostics.split(" +");

        assertEquals("rel", tokens[0]);
        assertEquals("s/r/x", tokens[1]);

        int previousReasonIndex = 1;
        for (String label : EXPECTED_REASON_LABELS) {
            int reasonIndex = tokenIndex(tokens, label);
            assertTrue("reason order drifted for " + label + ": " + diagnostics,
                    reasonIndex > previousReasonIndex);
            assertEquals("2/3/4", tokens[reasonIndex + 1]);
            assertEquals("sup=5", tokens[reasonIndex + 2]);
            previousReasonIndex = reasonIndex;
        }
    }

    private static Set<String> classifiedFamilies() {
        Set<String> classified = new TreeSet<>();
        classified.addAll(PRIORITY_FAMILIES);
        classified.addAll(SECONDARY_FAMILIES);
        assertEquals(
                "priority and secondary grammar classifications must not overlap",
                PRIORITY_FAMILIES.size() + SECONDARY_FAMILIES.size(),
                classified.size());
        return classified;
    }

    private static Set<String> producerDetailFamiliesFromSource() throws IOException {
        String diagnostics = diagnosticsMethodSource(producerSource());
        Set<String> families = new TreeSet<>();
        collectFamilies(DIRECT_DETAIL_APPEND, diagnostics, families);
        collectFamilies(SPACED_DETAIL_APPEND, diagnostics, families);
        return families;
    }

    private static String[] mappedValuesFromProducerMethod(
            String signature, Pattern pattern) throws IOException {
        String method = methodSource(producerSource(), signature);
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        Matcher matcher = pattern.matcher(method);
        while (matcher.find()) values.add(matcher.group(1));
        return values.toArray(new String[0]);
    }

    private static String producerSource() throws IOException {
        return new String(Files.readAllBytes(findProducerSource()), StandardCharsets.UTF_8);
    }

    private static void collectFamilies(
            Pattern pattern, String source, Set<String> families) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            families.add(matcher.group(1));
        }
    }

    private static String diagnosticsMethodSource(String source) {
        return methodSource(source, "String diagnostics() {");
    }

    private static String methodSource(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        assertTrue("cannot locate producer method: " + signature, signatureStart >= 0);

        int openBrace = source.indexOf('{', signatureStart);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') {
                depth++;
            } else if (value == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(signatureStart, i + 1);
                }
            }
        }
        throw new AssertionError("cannot find end of producer method: " + signature);
    }

    private static Path findProducerSource() {
        String relative = "src/main/java/com/livecopilot/micprobe/RouteReleaseOutcomeStats.java";
        Path cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int depth = 0; depth < 5 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path moduleRelative = cursor.resolve(relative);
            if (Files.isRegularFile(moduleRelative)) return moduleRelative;

            Path rootRelative = cursor.resolve("app").resolve(relative);
            if (Files.isRegularFile(rootRelative)) return rootRelative;
        }
        throw new AssertionError(
                "cannot locate RouteReleaseOutcomeStats.java from "
                        + System.getProperty("user.dir"));
    }

    private static Set<String> emittedDetailFamilies(String diagnostics) {
        Set<String> families = new TreeSet<>();
        for (String token : diagnostics.trim().split(" +")) {
            if ("rel".equals(token)
                    || "s/r/x".equals(token)
                    || REASON_LABELS.contains(token)
                    || isCountTriple(token)) {
                continue;
            }

            int equals = token.indexOf('=');
            if (equals > 0) {
                families.add(token.substring(0, equals));
            } else if (token.startsWith("rconf")) {
                families.add("rconf");
            } else if (token.startsWith("conf")) {
                families.add("conf");
            } else if (token.startsWith("rev")) {
                families.add("rev");
            } else {
                throw new AssertionError(
                        "unclassified runtime diagnostic token: " + token
                                + " in " + diagnostics);
            }
        }
        return families;
    }

    private static boolean isCountTriple(String token) {
        if (token == null || token.isEmpty()) return false;
        int separators = 0;
        boolean hasDigit = false;
        for (int i = 0; i < token.length(); i++) {
            char value = token.charAt(i);
            if (value == '/') {
                if (!hasDigit || separators >= 2) return false;
                separators++;
                hasDigit = false;
            } else if (Character.isDigit(value)) {
                hasDigit = true;
            } else {
                return false;
            }
        }
        return separators == 2 && hasDigit;
    }

    private static void record(
            RouteReleaseOutcomeStats stats, String reason, String outcome, int count) {
        for (int i = 0; i < count; i++) stats.record(reason, outcome);
    }

    private static int tokenIndex(String[] tokens, String expected) {
        for (int i = 0; i < tokens.length; i++) {
            if (expected.equals(tokens[i])) return i;
        }
        return -1;
    }

    private static String normalizeFamily(String prefix) {
        String normalized = prefix;
        while (normalized.endsWith("=")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
