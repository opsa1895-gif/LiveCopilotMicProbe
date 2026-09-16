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
text = replace_once(text, 'versionCode = 99', 'versionCode = 100', 'versionCode')
text = replace_once(
    text,
    'versionName = "0.98.0-recent-transition-confirmation-window"',
    'versionName = "0.99.0-transition-confirmation-reliability"',
    'versionName',
)
gradle.write_text(text)

# Local confirmation-rate reliability labels. These are diagnostics only and do not feed routing.
stats = root / "app/src/main/java/com/livecopilot/micprobe/RouteReleaseOutcomeStats.java"
text = stats.read_text()
text = replace_once(
    text,
    '    static final int TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES = 8;\n',
    '    static final int TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES = 8;\n'
    '    static final int STRONG_TRANSITION_CONFIRMATION_RATE_MIN_PERCENT = 75;\n'
    '    static final int WEAK_TRANSITION_CONFIRMATION_RATE_MAX_PERCENT = 25;\n',
    'reliability thresholds',
)
text = replace_once(
    text,
    '''    int transitionConfirmationRatePercent(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return -1;\n        return transitionConfirmationRatePercent(reason);\n    }\n\n''',
    '''    int transitionConfirmationRatePercent(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return -1;\n        return transitionConfirmationRatePercent(reason);\n    }\n\n    String transitionConfirmationReliabilityLabel(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return "-";\n        return transitionConfirmationReliabilityLabel(reason);\n    }\n\n''',
    'public reliability label',
)
text = replace_once(
    text,
    '''                    out.append(" conf").append(transitionConfirmationRatePercent(reason)).append("%@")\n                            .append(confirmationSamples);\n''',
    '''                    out.append(" conf").append(transitionConfirmationRatePercent(reason)).append("%@")\n                            .append(confirmationSamples).append('/')\n                            .append(transitionConfirmationReliabilityLabel(reason));\n''',
    'diagnostics reliability label',
)
text = replace_once(
    text,
    '''    private String reversalSignalLabel(int reason) {\n''',
    '''    static String transitionConfirmationReliabilityLabelForRate(\n            int samples, int confirmationRatePercent) {\n        if (samples < MIN_TRANSITION_CONFIRMATION_RATE_SAMPLES) return "learn";\n        if (confirmationRatePercent >= STRONG_TRANSITION_CONFIRMATION_RATE_MIN_PERCENT) {\n            return "strong";\n        }\n        if (confirmationRatePercent <= WEAK_TRANSITION_CONFIRMATION_RATE_MAX_PERCENT) {\n            return "weak";\n        }\n        return "mixed";\n    }\n\n    private String transitionConfirmationReliabilityLabel(int reason) {\n        int samples = transitionConfirmationRateSampleCount(reason);\n        return transitionConfirmationReliabilityLabelForRate(\n                samples, transitionConfirmationRatePercent(reason));\n    }\n\n    private String reversalSignalLabel(int reason) {\n''',
    'reliability classifier',
)
stats.write_text(text)

# Focused regression coverage for the new diagnostic-only classification.
test_path = root / "app/src/test/java/com/livecopilot/micprobe/RouteReleaseTransitionConfirmationReliabilityTest.java"
if test_path.exists():
    raise RuntimeError(f"test file already exists: {test_path}")
test_path.write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertTrue;\n\npublic class RouteReleaseTransitionConfirmationReliabilityTest {\n    @Test\n    public void classifierUsesLearnStrongMixedAndWeakBoundaries() {\n        assertEquals("learn",\n                RouteReleaseOutcomeStats.transitionConfirmationReliabilityLabelForRate(1, 100));\n        assertEquals("strong",\n                RouteReleaseOutcomeStats.transitionConfirmationReliabilityLabelForRate(2, 75));\n        assertEquals("mixed",\n                RouteReleaseOutcomeStats.transitionConfirmationReliabilityLabelForRate(2, 74));\n        assertEquals("mixed",\n                RouteReleaseOutcomeStats.transitionConfirmationReliabilityLabelForRate(2, 26));\n        assertEquals("weak",\n                RouteReleaseOutcomeStats.transitionConfirmationReliabilityLabelForRate(2, 25));\n    }\n\n    @Test\n    public void unresolvedAndUnknownReasonsDoNotPretendToBeReliable() {\n        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();\n\n        assertEquals("-", stats.transitionConfirmationReliabilityLabel("unknown"));\n        assertEquals("learn", stats.transitionConfirmationReliabilityLabel("rt-slowdown"));\n\n        for (int i = 0; i < RouteReleaseOutcomeStats.REVERSAL_RATE_WINDOW_SAMPLES; i++) {\n            stats.record("rt-slowdown", "reversal");\n        }\n        for (int i = 0; i < 6; i++) {\n            stats.record("rt-slowdown", "stable");\n        }\n\n        assertEquals(1, stats.supersededSignalTransitionCount("rt-slowdown"));\n        assertEquals(0, stats.transitionConfirmationRateSampleCount("rt-slowdown"));\n        assertEquals("learn", stats.transitionConfirmationReliabilityLabel("rt-slowdown"));\n    }\n\n    @Test\n    public void twoConfirmedTransitionsProduceStrongRecentReliability() {\n        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();\n        String[] outcomes = {\n                "stable", "stable", "stable",\n                "reversal", "reversal", "stable",\n                "reversal", "reversal", "reversal"\n        };\n        for (String outcome : outcomes) {\n            stats.record("rt-speedup", outcome);\n        }\n\n        assertEquals(2, stats.confirmedSignalTransitionCount("rt-speedup"));\n        assertEquals(0, stats.revertedSignalTransitionCount("rt-speedup"));\n        assertEquals(2, stats.transitionConfirmationRateSampleCount("rt-speedup"));\n        assertEquals(100, stats.transitionConfirmationRatePercent("rt-speedup"));\n        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("rt-speedup"));\n        assertTrue(stats.diagnostics().contains("conf100%@2/strong"));\n    }\n\n    @Test\n    public void balancedConfirmedAndRevertedHistoryIsMixed() {\n        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();\n        String[] outcomes = {\n                "stable", "stable", "reversal", "stable", "stable",\n                "stable", "reversal", "stable", "reversal", "reversal"\n        };\n        for (String outcome : outcomes) {\n            stats.record("file-speedup", outcome);\n        }\n\n        assertEquals(1, stats.confirmedSignalTransitionCount("file-speedup"));\n        assertEquals(1, stats.revertedSignalTransitionCount("file-speedup"));\n        assertEquals(50, stats.transitionConfirmationRatePercent("file-speedup"));\n        assertEquals("mixed", stats.transitionConfirmationReliabilityLabel("file-speedup"));\n        assertTrue(stats.diagnostics().contains("conf50%@2/mixed"));\n    }\n\n    @Test\n    public void recentWindowCanAgeIntoWeakReliabilityAndClearReturnsToLearn() {\n        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();\n        String[] cycle = {"stable", "stable", "stable", "reversal", "reversal", "stable"};\n        for (int repeat = 0; repeat < 10; repeat++) {\n            for (String outcome : cycle) {\n                stats.record("file-slowdown", outcome);\n            }\n        }\n\n        assertEquals(RouteReleaseOutcomeStats.TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES,\n                stats.transitionConfirmationRateSampleCount("file-slowdown"));\n        assertEquals(0, stats.transitionConfirmationRatePercent("file-slowdown"));\n        assertEquals("weak", stats.transitionConfirmationReliabilityLabel("file-slowdown"));\n        assertTrue(stats.diagnostics().contains("conf0%@8/weak"));\n\n        stats.clear();\n\n        assertEquals(0, stats.transitionConfirmationRateSampleCount("file-slowdown"));\n        assertEquals(-1, stats.transitionConfirmationRatePercent("file-slowdown"));\n        assertEquals("learn", stats.transitionConfirmationReliabilityLabel("file-slowdown"));\n    }\n}\n''')
