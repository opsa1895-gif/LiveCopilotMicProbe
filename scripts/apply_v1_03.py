from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


root = Path(__file__).resolve().parents[1]

gradle = root / "app/build.gradle.kts"
text = gradle.read_text()
text = replace_once(text, 'versionCode = 103', 'versionCode = 104', 'versionCode')
text = replace_once(
    text,
    'versionName = "1.02.0-reliability-cancel-reasons"',
    'versionName = "1.03.0-reliability-transition-confirmation-rate"',
    'versionName',
)
gradle.write_text(text)

stats = root / "app/src/main/java/com/livecopilot/micprobe/RouteReleaseOutcomeStats.java"
text = stats.read_text()
text = replace_once(
    text,
    '    static final int RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS = 2;\n',
    '    static final int RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS = 2;\n'
    '    static final int MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES = 2;\n',
    'reliability transition rate threshold',
)
text = replace_once(
    text,
    '''    int supersededTransitionReliabilityChangeCount(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return supersededTransitionReliabilityChanges[reason];\n    }\n\n    static String transitionReliabilityCancellationReason(\n''',
    '''    int supersededTransitionReliabilityChangeCount(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return supersededTransitionReliabilityChanges[reason];\n    }\n\n    int reliabilityTransitionConfirmationRateSampleCount(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return reliabilityTransitionConfirmationRateSampleCountForCounts(\n                confirmedTransitionReliabilityChanges[reason],\n                revertedTransitionReliabilityChanges[reason]);\n    }\n\n    int reliabilityTransitionConfirmationRatePercent(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return -1;\n        return reliabilityTransitionConfirmationRatePercentForCounts(\n                confirmedTransitionReliabilityChanges[reason],\n                revertedTransitionReliabilityChanges[reason]);\n    }\n\n    static int reliabilityTransitionConfirmationRateSampleCountForCounts(\n            int confirmed, int reverted) {\n        long samples = (long) Math.max(0, confirmed) + Math.max(0, reverted);\n        return samples >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) samples;\n    }\n\n    static int reliabilityTransitionConfirmationRatePercentForCounts(\n            int confirmed, int reverted) {\n        int samples = reliabilityTransitionConfirmationRateSampleCountForCounts(\n                confirmed, reverted);\n        if (samples < MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) return -1;\n        return (int) (((long) Math.max(0, confirmed) * 100L) / samples);\n    }\n\n    static String transitionReliabilityCancellationReason(\n''',
    'reliability transition confirmation rate methods',
)
text = replace_once(
    text,
    '''                        if (canceledTransitionReliabilityChanges[reason] > 0) {\n                            out.append(" rcancel=").append(revertedTransitionReliabilityChanges[reason])\n                                    .append('/').append(supersededTransitionReliabilityChanges[reason]);\n                        }\n                    }\n''',
    '''                        if (canceledTransitionReliabilityChanges[reason] > 0) {\n                            out.append(" rcancel=").append(revertedTransitionReliabilityChanges[reason])\n                                    .append('/').append(supersededTransitionReliabilityChanges[reason]);\n                        }\n                        int reliabilityTransitionSamples =\n                                reliabilityTransitionConfirmationRateSampleCountForCounts(\n                                        confirmedTransitionReliabilityChanges[reason],\n                                        revertedTransitionReliabilityChanges[reason]);\n                        if (reliabilityTransitionSamples\n                                >= MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) {\n                            out.append(" rconf")\n                                    .append(reliabilityTransitionConfirmationRatePercentForCounts(\n                                            confirmedTransitionReliabilityChanges[reason],\n                                            revertedTransitionReliabilityChanges[reason]))\n                                    .append("%@").append(reliabilityTransitionSamples);\n                        }\n                    }\n''',
    'reliability transition rate diagnostics',
)
stats.write_text(text)

test = root / "app/src/test/java/com/livecopilot/micprobe/RouteReleaseTransitionReliabilityConfirmationRateTest.java"
if test.exists():
    raise RuntimeError(f"test file already exists: {test}")
test.write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertEquals;\n\npublic class RouteReleaseTransitionReliabilityConfirmationRateTest {\n    @Test\n    public void rateRequiresTwoResolvedConfirmationOrReversionOutcomes() {\n        assertEquals(0, RouteReleaseOutcomeStats\n                .reliabilityTransitionConfirmationRateSampleCountForCounts(0, 0));\n        assertEquals(1, RouteReleaseOutcomeStats\n                .reliabilityTransitionConfirmationRateSampleCountForCounts(1, 0));\n        assertEquals(-1, RouteReleaseOutcomeStats\n                .reliabilityTransitionConfirmationRatePercentForCounts(1, 0));\n        assertEquals(-1, RouteReleaseOutcomeStats\n                .reliabilityTransitionConfirmationRatePercentForCounts(0, 1));\n        assertEquals(50, RouteReleaseOutcomeStats\n                .reliabilityTransitionConfirmationRatePercentForCounts(1, 1));\n        assertEquals(100, RouteReleaseOutcomeStats\n                .reliabilityTransitionConfirmationRatePercentForCounts(2, 0));\n        assertEquals(0, RouteReleaseOutcomeStats\n                .reliabilityTransitionConfirmationRatePercentForCounts(0, 2));\n    }\n\n    @Test\n    public void supersededIsExcludedFromRateDenominatorByConstruction() {\n        assertEquals(4, RouteReleaseOutcomeStats\n                .reliabilityTransitionConfirmationRateSampleCountForCounts(3, 1));\n        assertEquals(75, RouteReleaseOutcomeStats\n                .reliabilityTransitionConfirmationRatePercentForCounts(3, 1));\n    }\n\n    @Test\n    public void realReversionFeedsRateAndClearResetsIt() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("file-speedup");\n        add(stats, "file-speedup", "ssssr");\n        add(stats, "file-speedup", "sr");\n\n        assertEquals(1, stats.revertedTransitionReliabilityChangeCount("file-speedup"));\n        assertEquals(1, stats.reliabilityTransitionConfirmationRateSampleCount("file-speedup"));\n        assertEquals(-1, stats.reliabilityTransitionConfirmationRatePercent("file-speedup"));\n        assertEquals(0, stats.reliabilityTransitionConfirmationRateSampleCount("unknown"));\n        assertEquals(-1, stats.reliabilityTransitionConfirmationRatePercent("unknown"));\n\n        stats.clear();\n\n        assertEquals(0, stats.reliabilityTransitionConfirmationRateSampleCount("file-speedup"));\n        assertEquals(-1, stats.reliabilityTransitionConfirmationRatePercent("file-speedup"));\n    }\n\n    @Test\n    public void negativeSyntheticCountsAreClampedForHelperSafety() {\n        assertEquals(2, RouteReleaseOutcomeStats\n                .reliabilityTransitionConfirmationRateSampleCountForCounts(-3, 2));\n        assertEquals(0, RouteReleaseOutcomeStats\n                .reliabilityTransitionConfirmationRatePercentForCounts(-3, 2));\n    }\n\n    private static RouteReleaseOutcomeStats stronglyConfirmed(String reason) {\n        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();\n        add(stats, reason, "sssrrsrrr");\n        assertEquals("strong", stats.transitionConfirmationReliabilityLabel(reason));\n        return stats;\n    }\n\n    private static void add(RouteReleaseOutcomeStats stats, String reason, String sequence) {\n        for (int i = 0; i < sequence.length(); i++) {\n            char sample = sequence.charAt(i);\n            stats.record(reason, sample == 'r' ? "reversal" : "stable");\n        }\n    }\n}\n''')
