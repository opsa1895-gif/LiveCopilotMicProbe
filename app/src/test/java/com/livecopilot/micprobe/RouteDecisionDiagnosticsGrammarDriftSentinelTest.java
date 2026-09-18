package com.livecopilot.micprobe;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
    private static final Set<String> REASON_LABELS = Set.of(
            "rtslow", "rtfast", "ffast", "fslow");
    private static final String RICH_GRAMMAR_SEQUENCE = "srsrsrsrssrsssr";

    private static final Pattern DIRECT_DETAIL_APPEND = Pattern.compile(
            "\\\\.append\\\\(\\\\\" ([A-Za-z][A-Za-z0-9]*)(?:=)?");
    private static final Pattern SPACED_DETAIL_APPEND = Pattern.compile(
            "\\\\.append\\\\(' '\\\\)\\\\s*\\\\.append\\\\(\\\\\"([A-Za-z][A-Za-z0-9]*)(?:=)?");

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
        String source = Files.readString(findProducerSource());
        String diagnostics = diagnosticsMethodSource(source);
        Set<String> families = new TreeSet<>();
        collectFamilies(DIRECT_DETAIL_APPEND, diagnostics, families);
        collectFamilies(SPACED_DETAIL_APPEND, diagnostics, families);
        return families;
    }

    private static void collectFamilies(
            Pattern pattern, String source, Set<String> families) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            families.add(matcher.group(1));
        }
    }

    private static String diagnosticsMethodSource(String source) {
        String signature = "String diagnostics() {";
        int signatureStart = source.indexOf(signature);
        assertTrue("cannot locate RouteReleaseOutcomeStats.diagnostics()", signatureStart >= 0);

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
        throw new AssertionError("cannot find end of RouteReleaseOutcomeStats.diagnostics()");
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
