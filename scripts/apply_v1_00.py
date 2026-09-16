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
text = replace_once(text, 'versionCode = 100', 'versionCode = 101', 'versionCode')
text = replace_once(
    text,
    'versionName = "0.99.0-transition-confirmation-reliability"',
    'versionName = "1.00.0-transition-reliability-hysteresis"',
    'versionName',
)
gradle.write_text(text)

# Diagnostic-only hysteresis for the transition-confirmation reliability label.
stats = root / "app/src/main/java/com/livecopilot/micprobe/RouteReleaseOutcomeStats.java"
text = stats.read_text()
text = replace_once(
    text,
    '    static final int WEAK_TRANSITION_CONFIRMATION_RATE_MAX_PERCENT = 25;\n',
    '    static final int WEAK_TRANSITION_CONFIRMATION_RATE_MAX_PERCENT = 25;\n'
    '    static final int RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS = 2;\n',
    'reliability hysteresis constant',
)
text = replace_once(
    text,
    '    private final int[] recentTransitionConfirmations = new int[REASON_COUNT];\n',
    '    private final int[] recentTransitionConfirmations = new int[REASON_COUNT];\n'
    '    private final String[] latchedTransitionReliabilityLabels = new String[REASON_COUNT];\n'
    '    private final String[] candidateTransitionReliabilityLabels = new String[REASON_COUNT];\n'
    '    private final int[] candidateTransitionReliabilityStreak = new int[REASON_COUNT];\n',
    'reliability hysteresis state',
)
text = replace_once(
    text,
    '''    String transitionConfirmationReliabilityLabel(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return "-";\n        return transitionConfirmationReliabilityLabel(reason);\n    }\n\n''',
    '''    String transitionConfirmationReliabilityLabel(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return "-";\n        return transitionConfirmationReliabilityLabel(reason);\n    }\n\n    String transitionConfirmationReliabilityPendingLabel(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return "-";\n        String candidate = candidateTransitionReliabilityLabels[reason];\n        return candidate != null ? candidate : "-";\n    }\n\n    int transitionConfirmationReliabilityPendingStreak(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return candidateTransitionReliabilityStreak[reason];\n    }\n\n''',
    'reliability pending accessors',
)
text = replace_once(
    text,
    '''            recentTransitionResolutionNext[reason] = 0;\n            recentTransitionResolutionSize[reason] = 0;\n            recentTransitionConfirmations[reason] = 0;\n''',
    '''            recentTransitionResolutionNext[reason] = 0;\n            recentTransitionResolutionSize[reason] = 0;\n            recentTransitionConfirmations[reason] = 0;\n            latchedTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n''',
    'clear reliability hysteresis',
)
text = replace_once(
    text,
    '''                    out.append(" conf").append(transitionConfirmationRatePercent(reason)).append("%@")\n                            .append(confirmationSamples).append('/')\n                            .append(transitionConfirmationReliabilityLabel(reason));\n''',
    '''                    out.append(" conf").append(transitionConfirmationRatePercent(reason)).append("%@")\n                            .append(confirmationSamples).append('/')\n                            .append(transitionConfirmationReliabilityLabel(reason));\n                    if (candidateTransitionReliabilityLabels[reason] != null\n                            && candidateTransitionReliabilityStreak[reason] > 0) {\n                        out.append('>').append(candidateTransitionReliabilityLabels[reason]).append('×')\n                                .append(candidateTransitionReliabilityStreak[reason]).append('/')\n                                .append(RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS);\n                    }\n''',
    'reliability pending diagnostics',
)
text = replace_once(
    text,
    '''    private String transitionConfirmationReliabilityLabel(int reason) {\n        int samples = transitionConfirmationRateSampleCount(reason);\n        return transitionConfirmationReliabilityLabelForRate(\n                samples, transitionConfirmationRatePercent(reason));\n    }\n\n''',
    '''    private String transitionConfirmationReliabilityLabel(int reason) {\n        int samples = transitionConfirmationRateSampleCount(reason);\n        if (samples < MIN_TRANSITION_CONFIRMATION_RATE_SAMPLES) return "learn";\n        String latched = latchedTransitionReliabilityLabels[reason];\n        return latched != null ? latched : rawTransitionConfirmationReliabilityLabel(reason);\n    }\n\n    private String rawTransitionConfirmationReliabilityLabel(int reason) {\n        return transitionConfirmationReliabilityLabelForRate(\n                transitionConfirmationRateSampleCount(reason),\n                transitionConfirmationRatePercent(reason));\n    }\n\n''',
    'latched reliability getter',
)
text = replace_once(
    text,
    '''        recentTransitionResolutionNext[reason] =\n                (next + 1) % TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES;\n    }\n\n    private void updateSignalLabel(int reason) {\n''',
    '''        recentTransitionResolutionNext[reason] =\n                (next + 1) % TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES;\n        updateTransitionReliabilityLabel(reason);\n    }\n\n    private void updateTransitionReliabilityLabel(int reason) {\n        if (recentTransitionResolutionSize[reason] < MIN_TRANSITION_CONFIRMATION_RATE_SAMPLES) {\n            latchedTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n            return;\n        }\n\n        String rawLabel = rawTransitionConfirmationReliabilityLabel(reason);\n        String latchedLabel = latchedTransitionReliabilityLabels[reason];\n        if (latchedLabel == null) {\n            latchedTransitionReliabilityLabels[reason] = rawLabel;\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n            return;\n        }\n        if (rawLabel.equals(latchedLabel)) {\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n            return;\n        }\n        if (rawLabel.equals(candidateTransitionReliabilityLabels[reason])) {\n            candidateTransitionReliabilityStreak[reason]++;\n        } else {\n            candidateTransitionReliabilityLabels[reason] = rawLabel;\n            candidateTransitionReliabilityStreak[reason] = 1;\n        }\n        if (candidateTransitionReliabilityStreak[reason] >= RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS) {\n            latchedTransitionReliabilityLabels[reason] = rawLabel;\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n        }\n    }\n\n    private void updateSignalLabel(int reason) {\n''',
    'reliability hysteresis update',
)
stats.write_text(text)

# Focused regression coverage for diagnostic reliability hysteresis.
test_path = root / "app/src/test/java/com/livecopilot/micprobe/RouteReleaseTransitionConfirmationReliabilityHysteresisTest.java"
if test_path.exists():
    raise RuntimeError(f"test file already exists: {test_path}")
test_path.write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n\npublic class RouteReleaseTransitionConfirmationReliabilityHysteresisTest {\n    @Test\n    public void singleBoundaryCrossingIsHeldUntilSecondResolvedObservation() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-speedup");\n\n        add(stats, "rt-speedup", "ssssr");\n\n        assertEquals(3, stats.transitionConfirmationRateSampleCount("rt-speedup"));\n        assertEquals(66, stats.transitionConfirmationRatePercent("rt-speedup"));\n        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("rt-speedup"));\n        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("rt-speedup"));\n        assertEquals(1, stats.transitionConfirmationReliabilityPendingStreak("rt-speedup"));\n        assertTrue(stats.diagnostics().contains("conf66%@3/strong>mixed×1/2"));\n\n        add(stats, "rt-speedup", "rrsr");\n\n        assertEquals(4, stats.transitionConfirmationRateSampleCount("rt-speedup"));\n        assertEquals(50, stats.transitionConfirmationRatePercent("rt-speedup"));\n        assertEquals("mixed", stats.transitionConfirmationReliabilityLabel("rt-speedup"));\n        assertEquals("-", stats.transitionConfirmationReliabilityPendingLabel("rt-speedup"));\n        assertEquals(0, stats.transitionConfirmationReliabilityPendingStreak("rt-speedup"));\n        assertTrue(stats.diagnostics().contains("conf50%@4/mixed"));\n        assertFalse(stats.diagnostics().contains("conf50%@4/mixed>"));\n    }\n\n    @Test\n    public void returnToLatchedReliabilityCancelsPendingCandidate() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("file-speedup");\n        add(stats, "file-speedup", "ssssr");\n        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("file-speedup"));\n\n        add(stats, "file-speedup", "sr");\n\n        assertEquals(4, stats.transitionConfirmationRateSampleCount("file-speedup"));\n        assertEquals(75, stats.transitionConfirmationRatePercent("file-speedup"));\n        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("file-speedup"));\n        assertEquals("-", stats.transitionConfirmationReliabilityPendingLabel("file-speedup"));\n        assertEquals(0, stats.transitionConfirmationReliabilityPendingStreak("file-speedup"));\n        assertTrue(stats.diagnostics().contains("conf75%@4/strong"));\n    }\n\n    @Test\n    public void reliabilityHysteresisIsIndependentPerReason() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-slowdown");\n        add(stats, "file-slowdown", "sssrrsrrr");\n\n        add(stats, "rt-slowdown", "ssssr");\n\n        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("rt-slowdown"));\n        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("rt-slowdown"));\n        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("file-slowdown"));\n        assertEquals("-", stats.transitionConfirmationReliabilityPendingLabel("file-slowdown"));\n    }\n\n    @Test\n    public void clearDropsLatchedAndPendingReliabilityState() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-slowdown");\n        add(stats, "rt-slowdown", "ssssr");\n        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("rt-slowdown"));\n\n        stats.clear();\n\n        assertEquals("learn", stats.transitionConfirmationReliabilityLabel("rt-slowdown"));\n        assertEquals("-", stats.transitionConfirmationReliabilityPendingLabel("rt-slowdown"));\n        assertEquals(0, stats.transitionConfirmationReliabilityPendingStreak("rt-slowdown"));\n        assertEquals("", stats.diagnostics());\n    }\n\n    private static RouteReleaseOutcomeStats stronglyConfirmed(String reason) {\n        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();\n        add(stats, reason, "sssrrsrrr");\n        assertEquals(2, stats.transitionConfirmationRateSampleCount(reason));\n        assertEquals(100, stats.transitionConfirmationRatePercent(reason));\n        assertEquals("strong", stats.transitionConfirmationReliabilityLabel(reason));\n        return stats;\n    }\n\n    private static void add(RouteReleaseOutcomeStats stats, String reason, String sequence) {\n        for (int i = 0; i < sequence.length(); i++) {\n            char sample = sequence.charAt(i);\n            stats.record(reason, sample == 'r' ? "reversal" : "stable");\n        }\n    }\n}\n''')
