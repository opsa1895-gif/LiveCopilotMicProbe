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
text = replace_once(text, 'versionCode = 101', 'versionCode = 102', 'versionCode')
text = replace_once(
    text,
    'versionName = "1.00.0-transition-reliability-hysteresis"',
    'versionName = "1.01.0-reliability-transition-outcomes"',
    'versionName',
)
gradle.write_text(text)

# Local-only observability for reliability-label hysteresis outcomes.
stats = root / "app/src/main/java/com/livecopilot/micprobe/RouteReleaseOutcomeStats.java"
text = stats.read_text()
text = replace_once(
    text,
    '    private final int[] candidateTransitionReliabilityStreak = new int[REASON_COUNT];\n',
    '    private final int[] candidateTransitionReliabilityStreak = new int[REASON_COUNT];\n'
    '    private final int[] confirmedTransitionReliabilityChanges = new int[REASON_COUNT];\n'
    '    private final int[] canceledTransitionReliabilityChanges = new int[REASON_COUNT];\n',
    'reliability transition counters',
)
text = replace_once(
    text,
    '''    int transitionConfirmationReliabilityPendingStreak(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return candidateTransitionReliabilityStreak[reason];\n    }\n\n''',
    '''    int transitionConfirmationReliabilityPendingStreak(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return candidateTransitionReliabilityStreak[reason];\n    }\n\n    int confirmedTransitionReliabilityChangeCount(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return confirmedTransitionReliabilityChanges[reason];\n    }\n\n    int canceledTransitionReliabilityChangeCount(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return canceledTransitionReliabilityChanges[reason];\n    }\n\n''',
    'reliability transition accessors',
)
text = replace_once(
    text,
    '''            recentTransitionConfirmations[reason] = 0;\n            latchedTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n''',
    '''            recentTransitionConfirmations[reason] = 0;\n            latchedTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n            confirmedTransitionReliabilityChanges[reason] = 0;\n            canceledTransitionReliabilityChanges[reason] = 0;\n''',
    'clear reliability transition counters',
)
text = replace_once(
    text,
    '''                    if (candidateTransitionReliabilityLabels[reason] != null\n                            && candidateTransitionReliabilityStreak[reason] > 0) {\n                        out.append('>').append(candidateTransitionReliabilityLabels[reason]).append('×')\n                                .append(candidateTransitionReliabilityStreak[reason]).append('/')\n                                .append(RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS);\n                    }\n''',
    '''                    if (candidateTransitionReliabilityLabels[reason] != null\n                            && candidateTransitionReliabilityStreak[reason] > 0) {\n                        out.append('>').append(candidateTransitionReliabilityLabels[reason]).append('×')\n                                .append(candidateTransitionReliabilityStreak[reason]).append('/')\n                                .append(RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS);\n                    }\n                    if (confirmedTransitionReliabilityChanges[reason] > 0\n                            || canceledTransitionReliabilityChanges[reason] > 0) {\n                        out.append(" rtr=").append(confirmedTransitionReliabilityChanges[reason])\n                                .append('/').append(canceledTransitionReliabilityChanges[reason]);\n                    }\n''',
    'reliability transition diagnostics',
)
text = replace_once(
    text,
    '''        if (rawLabel.equals(latchedLabel)) {\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n            return;\n        }\n        if (rawLabel.equals(candidateTransitionReliabilityLabels[reason])) {\n            candidateTransitionReliabilityStreak[reason]++;\n        } else {\n            candidateTransitionReliabilityLabels[reason] = rawLabel;\n            candidateTransitionReliabilityStreak[reason] = 1;\n        }\n        if (candidateTransitionReliabilityStreak[reason] >= RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS) {\n            latchedTransitionReliabilityLabels[reason] = rawLabel;\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n        }\n    }\n\n    private void updateSignalLabel(int reason) {\n''',
    '''        if (rawLabel.equals(latchedLabel)) {\n            if (candidateTransitionReliabilityLabels[reason] != null) {\n                incrementCanceledTransitionReliabilityChange(reason);\n            }\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n            return;\n        }\n        if (rawLabel.equals(candidateTransitionReliabilityLabels[reason])) {\n            candidateTransitionReliabilityStreak[reason]++;\n        } else {\n            if (candidateTransitionReliabilityLabels[reason] != null) {\n                incrementCanceledTransitionReliabilityChange(reason);\n            }\n            candidateTransitionReliabilityLabels[reason] = rawLabel;\n            candidateTransitionReliabilityStreak[reason] = 1;\n        }\n        if (candidateTransitionReliabilityStreak[reason] >= RELIABILITY_CHANGE_CONFIRM_RESOLUTIONS) {\n            latchedTransitionReliabilityLabels[reason] = rawLabel;\n            incrementConfirmedTransitionReliabilityChange(reason);\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n        }\n    }\n\n    private void incrementConfirmedTransitionReliabilityChange(int reason) {\n        if (confirmedTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            confirmedTransitionReliabilityChanges[reason]++;\n        }\n    }\n\n    private void incrementCanceledTransitionReliabilityChange(int reason) {\n        if (canceledTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            canceledTransitionReliabilityChanges[reason]++;\n        }\n    }\n\n    private void updateSignalLabel(int reason) {\n''',
    'reliability transition accounting',
)
stats.write_text(text)

# Focused regression coverage for reliability-label transition outcomes.
test_path = root / "app/src/test/java/com/livecopilot/micprobe/RouteReleaseTransitionReliabilityOutcomeTest.java"
if test_path.exists():
    raise RuntimeError(f"test file already exists: {test_path}")
test_path.write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertTrue;\n\npublic class RouteReleaseTransitionReliabilityOutcomeTest {\n    @Test\n    public void initialReliabilityLatchIsNotCountedAsTransition() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-slowdown");\n\n        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("rt-slowdown"));\n        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("rt-slowdown"));\n        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("unknown"));\n        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("unknown"));\n    }\n\n    @Test\n    public void secondMatchingCandidateConfirmsReliabilityTransition() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-speedup");\n\n        add(stats, "rt-speedup", "ssssr");\n        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("rt-speedup"));\n        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("rt-speedup"));\n\n        add(stats, "rt-speedup", "rrsr");\n\n        assertEquals("mixed", stats.transitionConfirmationReliabilityLabel("rt-speedup"));\n        assertEquals(1, stats.confirmedTransitionReliabilityChangeCount("rt-speedup"));\n        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("rt-speedup"));\n        assertTrue(stats.diagnostics().contains("rtr=1/0"));\n    }\n\n    @Test\n    public void returnToLatchedReliabilityCancelsPendingTransition() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("file-speedup");\n\n        add(stats, "file-speedup", "ssssr");\n        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("file-speedup"));\n\n        add(stats, "file-speedup", "sr");\n\n        assertEquals("strong", stats.transitionConfirmationReliabilityLabel("file-speedup"));\n        assertEquals("-", stats.transitionConfirmationReliabilityPendingLabel("file-speedup"));\n        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("file-speedup"));\n        assertEquals(1, stats.canceledTransitionReliabilityChangeCount("file-speedup"));\n        assertTrue(stats.diagnostics().contains("rtr=0/1"));\n    }\n\n    @Test\n    public void countersAreIndependentPerReasonAndClearResetsThem() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-speedup");\n        add(stats, "rt-speedup", "ssssr");\n        add(stats, "rt-speedup", "rrsr");\n\n        stronglyConfirmedInto(stats, "file-speedup");\n        add(stats, "file-speedup", "ssssr");\n        add(stats, "file-speedup", "sr");\n\n        assertEquals(1, stats.confirmedTransitionReliabilityChangeCount("rt-speedup"));\n        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("rt-speedup"));\n        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("file-speedup"));\n        assertEquals(1, stats.canceledTransitionReliabilityChangeCount("file-speedup"));\n\n        stats.clear();\n\n        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("rt-speedup"));\n        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("rt-speedup"));\n        assertEquals(0, stats.confirmedTransitionReliabilityChangeCount("file-speedup"));\n        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("file-speedup"));\n        assertEquals("", stats.diagnostics());\n    }\n\n    private static RouteReleaseOutcomeStats stronglyConfirmed(String reason) {\n        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();\n        stronglyConfirmedInto(stats, reason);\n        return stats;\n    }\n\n    private static void stronglyConfirmedInto(RouteReleaseOutcomeStats stats, String reason) {\n        add(stats, reason, "sssrrsrrr");\n        assertEquals(2, stats.transitionConfirmationRateSampleCount(reason));\n        assertEquals(100, stats.transitionConfirmationRatePercent(reason));\n        assertEquals("strong", stats.transitionConfirmationReliabilityLabel(reason));\n    }\n\n    private static void add(RouteReleaseOutcomeStats stats, String reason, String sequence) {\n        for (int i = 0; i < sequence.length(); i++) {\n            char sample = sequence.charAt(i);\n            stats.record(reason, sample == 'r' ? "reversal" : "stable");\n        }\n    }\n}\n''')
