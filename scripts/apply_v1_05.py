from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


root = Path(__file__).resolve().parents[1]

gradle = root / "app/build.gradle.kts"
text = gradle.read_text()
text = replace_once(text, 'versionCode = 105', 'versionCode = 106', 'versionCode')
text = replace_once(
    text,
    'versionName = "1.04.0-recent-reliability-transition-rate"',
    'versionName = "1.05.0-reliability-sample-maturity"',
    'versionName',
)
gradle.write_text(text)

stats = root / "app/src/main/java/com/livecopilot/micprobe/RouteReleaseOutcomeStats.java"
text = stats.read_text()
text = replace_once(
    text,
    '''    int reliabilityTransitionConfirmationRatePercent(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return -1;\n        return recentReliabilityTransitionRates[reason].confirmationRatePercent(\n                MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES);\n    }\n\n''',
    '''    int reliabilityTransitionConfirmationRatePercent(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return -1;\n        return recentReliabilityTransitionRates[reason].confirmationRatePercent(\n                MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES);\n    }\n\n    String reliabilityTransitionSampleMaturityLabel(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return "-";\n        return reliabilityTransitionSampleMaturityLabelForSamples(\n                recentReliabilityTransitionRates[reason].sampleCount());\n    }\n\n''',
    'runtime maturity accessor',
)
text = replace_once(
    text,
    '''    static int reliabilityTransitionConfirmationRatePercentForCounts(\n            int confirmed, int reverted) {\n        int samples = reliabilityTransitionConfirmationRateSampleCountForCounts(\n                confirmed, reverted);\n        if (samples < MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) return -1;\n        return (int) (((long) Math.max(0, confirmed) * 100L) / samples);\n    }\n\n''',
    '''    static int reliabilityTransitionConfirmationRatePercentForCounts(\n            int confirmed, int reverted) {\n        int samples = reliabilityTransitionConfirmationRateSampleCountForCounts(\n                confirmed, reverted);\n        if (samples < MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) return -1;\n        return (int) (((long) Math.max(0, confirmed) * 100L) / samples);\n    }\n\n    static String reliabilityTransitionSampleMaturityLabelForSamples(int samples) {\n        int safeSamples = Math.max(0, samples);\n        if (safeSamples < MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) {\n            return "low";\n        }\n        if (safeSamples < RELIABILITY_TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES) {\n            return "usable";\n        }\n        return "mature";\n    }\n\n''',
    'maturity helper',
)
text = replace_once(
    text,
    '''                        int reliabilityTransitionSamples =\n                                recentReliabilityTransitionRates[reason].sampleCount();\n                        if (reliabilityTransitionSamples\n                                >= MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) {\n''',
    '''                        int reliabilityTransitionSamples =\n                                recentReliabilityTransitionRates[reason].sampleCount();\n                        out.append(" rmat=")\n                                .append(reliabilityTransitionSampleMaturityLabelForSamples(\n                                        reliabilityTransitionSamples));\n                        if (reliabilityTransitionSamples\n                                >= MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) {\n''',
    'diagnostic maturity label',
)
stats.write_text(text)

test = root / "app/src/test/java/com/livecopilot/micprobe/RouteReleaseTransitionReliabilityConfirmationRateTest.java"
text = test.read_text()
text = replace_once(
    text,
    'import static org.junit.Assert.assertEquals;\n',
    'import static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertTrue;\n',
    'assertTrue import',
)
text = replace_once(
    text,
    '''    @Test\n    public void negativeSyntheticCountsAreClampedForHelperSafety() {\n''',
    '''    @Test\n    public void sampleMaturityTracksRateReadinessAndFullWindow() {\n        assertEquals("low", RouteReleaseOutcomeStats\n                .reliabilityTransitionSampleMaturityLabelForSamples(-3));\n        assertEquals("low", RouteReleaseOutcomeStats\n                .reliabilityTransitionSampleMaturityLabelForSamples(0));\n        assertEquals("low", RouteReleaseOutcomeStats\n                .reliabilityTransitionSampleMaturityLabelForSamples(1));\n        assertEquals("usable", RouteReleaseOutcomeStats\n                .reliabilityTransitionSampleMaturityLabelForSamples(2));\n        assertEquals("usable", RouteReleaseOutcomeStats\n                .reliabilityTransitionSampleMaturityLabelForSamples(7));\n        assertEquals("mature", RouteReleaseOutcomeStats\n                .reliabilityTransitionSampleMaturityLabelForSamples(8));\n        assertEquals("mature", RouteReleaseOutcomeStats\n                .reliabilityTransitionSampleMaturityLabelForSamples(99));\n    }\n\n    @Test\n    public void runtimeMaturityIsExposedAlongsideRecentRate() {\n        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();\n        add(stats, "rt-speedup", "srsrsrsrssrsssr");\n\n        assertEquals("usable", stats.reliabilityTransitionSampleMaturityLabel("rt-speedup"));\n        assertEquals("-", stats.reliabilityTransitionSampleMaturityLabel("unknown"));\n        assertTrue(stats.diagnostics().contains("rmat=usable rconf50%@2"));\n\n        stats.clear();\n        assertEquals("low", stats.reliabilityTransitionSampleMaturityLabel("rt-speedup"));\n    }\n\n    @Test\n    public void negativeSyntheticCountsAreClampedForHelperSafety() {\n''',
    'maturity tests',
)
test.write_text(text)
