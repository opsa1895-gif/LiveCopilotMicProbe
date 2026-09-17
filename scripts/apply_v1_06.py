from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


root = Path(__file__).resolve().parents[1]

# Version bump.
gradle = root / "app/build.gradle.kts"
text = gradle.read_text()
text = replace_once(text, 'versionCode = 106', 'versionCode = 107', 'versionCode')
text = replace_once(
    text,
    'versionName = "1.05.0-reliability-sample-maturity"',
    'versionName = "1.06.0-superseded-release-outcome"',
    'versionName',
)
gradle.write_text(text)

# Record superseded pending regime releases as a fourth terminal observation bucket.
stats = root / "app/src/main/java/com/livecopilot/micprobe/RouteReleaseOutcomeStats.java"
text = stats.read_text()
text = replace_once(
    text,
    '''    private static final int OUTCOME_STABLE = 0;\n    private static final int OUTCOME_REVERSAL = 1;\n    private static final int OUTCOME_EXPIRED = 2;\n    private static final int OUTCOME_COUNT = 3;\n''',
    '''    private static final int OUTCOME_STABLE = 0;\n    private static final int OUTCOME_REVERSAL = 1;\n    private static final int OUTCOME_EXPIRED = 2;\n    private static final int OUTCOME_SUPERSEDED = 3;\n    private static final int OUTCOME_COUNT = 4;\n''',
    'superseded outcome constant',
)
text = replace_once(
    text,
    '''            out.append(' ').append(reasonLabel(reason)).append(' ')\n                    .append(counts[reason][OUTCOME_STABLE]).append('/')\n                    .append(counts[reason][OUTCOME_REVERSAL]).append('/')\n                    .append(counts[reason][OUTCOME_EXPIRED]);\n            int directionalSamples = recentDirectionalSize[reason];\n''',
    '''            out.append(' ').append(reasonLabel(reason)).append(' ')\n                    .append(counts[reason][OUTCOME_STABLE]).append('/')\n                    .append(counts[reason][OUTCOME_REVERSAL]).append('/')\n                    .append(counts[reason][OUTCOME_EXPIRED]);\n            if (counts[reason][OUTCOME_SUPERSEDED] > 0) {\n                out.append(" sup=").append(counts[reason][OUTCOME_SUPERSEDED]);\n            }\n            int directionalSamples = recentDirectionalSize[reason];\n''',
    'superseded diagnostics',
)
text = replace_once(
    text,
    '''        long total = (long) counts[reason][OUTCOME_STABLE]\n                + counts[reason][OUTCOME_REVERSAL]\n                + counts[reason][OUTCOME_EXPIRED];\n''',
    '''        long total = (long) counts[reason][OUTCOME_STABLE]\n                + counts[reason][OUTCOME_REVERSAL]\n                + counts[reason][OUTCOME_EXPIRED]\n                + counts[reason][OUTCOME_SUPERSEDED];\n''',
    'superseded reason total',
)
text = replace_once(
    text,
    '''        if ("stable".equals(outcome)) return OUTCOME_STABLE;\n        if ("reversal".equals(outcome)) return OUTCOME_REVERSAL;\n        if ("expired".equals(outcome)) return OUTCOME_EXPIRED;\n        return -1;\n''',
    '''        if ("stable".equals(outcome)) return OUTCOME_STABLE;\n        if ("reversal".equals(outcome)) return OUTCOME_REVERSAL;\n        if ("expired".equals(outcome)) return OUTCOME_EXPIRED;\n        if ("superseded".equals(outcome)) return OUTCOME_SUPERSEDED;\n        return -1;\n''',
    'superseded outcome index',
)
stats.write_text(text)

# Define replacement semantics in the pure routing policy.
policy = root / "app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java"
text = policy.read_text()
text = replace_once(
    text,
    '''        return nextStableStreak >= ROUTE_FLAP_STABLE_CONFIRM_TURNS ? "stable" : "pending";\n    }\n\n    static boolean isRouteFlapReversal(\n''',
    '''        return nextStableStreak >= ROUTE_FLAP_STABLE_CONFIRM_TURNS ? "stable" : "pending";\n    }\n\n    static String routeFlapReleaseReplacementOutcome(String currentOutcome) {\n        return "pending".equals(currentOutcome) ? "superseded" : "-";\n    }\n\n    static boolean isRouteFlapReversal(\n''',
    'release replacement outcome helper',
)
policy.write_text(text)

# Before installing a new qualified regime release, terminate any still-pending old one.
client = root / "app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java"
text = client.read_text()
text = replace_once(
    text,
    '''        if ("-".equals(releaseReason)) return;\n        lastRouteFlapReleaseReason = releaseReason;\n''',
    '''        if ("-".equals(releaseReason)) return;\n        String replacementOutcome = RealtimeRoutingPolicy.routeFlapReleaseReplacementOutcome(\n                lastRouteFlapReleaseOutcome);\n        if (!"-".equals(replacementOutcome)) {\n            routeFlapReleaseOutcomeStats.record(lastRouteFlapReleaseReason, replacementOutcome);\n        }\n        lastRouteFlapReleaseReason = releaseReason;\n''',
    'record superseded pending release before replacement',
)
client.write_text(text)

# Focused stats regression coverage.
stats_test = root / "app/src/test/java/com/livecopilot/micprobe/RouteReleaseOutcomeStatsTest.java"
text = stats_test.read_text()
text = replace_once(
    text,
    '''    @Test\n    public void pendingAndUnknownSignalsDoNotPolluteCompletedOutcomeCounts() {\n''',
    '''    @Test\n    public void supersededReleaseIsCountedButExcludedFromDirectionalRates() {\n        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();\n        stats.record("rt-slowdown", "stable");\n        stats.record("rt-slowdown", "reversal");\n        stats.record("rt-slowdown", "superseded");\n        stats.record("rt-slowdown", "superseded");\n\n        assertEquals(2, stats.count("rt-slowdown", "superseded"));\n        assertEquals(2, stats.reversalRateSampleCount("rt-slowdown"));\n        assertEquals(-1, stats.reversalRatePercent("rt-slowdown"));\n        assertEquals("learn", stats.reversalSignalLabel("rt-slowdown"));\n        assertEquals(\n                "rel s/r/x rtslow 1/1/0 sup=2 sig=learn",\n                stats.diagnostics());\n\n        stats.clear();\n        assertEquals(0, stats.count("rt-slowdown", "superseded"));\n        assertTrue(stats.isEmpty());\n    }\n\n    @Test\n    public void pendingAndUnknownSignalsDoNotPolluteCompletedOutcomeCounts() {\n''',
    'superseded stats test',
)
stats_test.write_text(text)

# Policy coverage for replacement-only semantics.
outcome_test = root / "app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingRegimeReleaseOutcomePolicyTest.java"
text = outcome_test.read_text()
text = replace_once(
    text,
    '''    @Test\n    public void unknownReleaseTargetDoesNotCreateOutcome() {\n''',
    '''    @Test\n    public void onlyPendingReleaseBecomesSupersededWhenReplaced() {\n        assertEquals("superseded",\n                RealtimeRoutingPolicy.routeFlapReleaseReplacementOutcome("pending"));\n        assertEquals("-", RealtimeRoutingPolicy.routeFlapReleaseReplacementOutcome("stable"));\n        assertEquals("-", RealtimeRoutingPolicy.routeFlapReleaseReplacementOutcome("reversal"));\n        assertEquals("-", RealtimeRoutingPolicy.routeFlapReleaseReplacementOutcome("expired"));\n        assertEquals("-", RealtimeRoutingPolicy.routeFlapReleaseReplacementOutcome("-"));\n        assertEquals("-", RealtimeRoutingPolicy.routeFlapReleaseReplacementOutcome(null));\n    }\n\n    @Test\n    public void unknownReleaseTargetDoesNotCreateOutcome() {\n''',
    'release replacement policy test',
)
outcome_test.write_text(text)
