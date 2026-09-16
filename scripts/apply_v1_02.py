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
text = replace_once(text, 'versionCode = 102', 'versionCode = 103', 'versionCode')
text = replace_once(
    text,
    'versionName = "1.01.0-reliability-transition-outcomes"',
    'versionName = "1.02.0-reliability-cancel-reasons"',
    'versionName',
)
gradle.write_text(text)

# Split reliability-transition cancellations into reverted vs superseded reasons.
stats = root / "app/src/main/java/com/livecopilot/micprobe/RouteReleaseOutcomeStats.java"
text = stats.read_text()
text = replace_once(
    text,
    '''    private final int[] confirmedTransitionReliabilityChanges = new int[REASON_COUNT];\n    private final int[] canceledTransitionReliabilityChanges = new int[REASON_COUNT];\n''',
    '''    private final int[] confirmedTransitionReliabilityChanges = new int[REASON_COUNT];\n    private final int[] canceledTransitionReliabilityChanges = new int[REASON_COUNT];\n    private final int[] revertedTransitionReliabilityChanges = new int[REASON_COUNT];\n    private final int[] supersededTransitionReliabilityChanges = new int[REASON_COUNT];\n''',
    'reliability cancellation counters',
)
text = replace_once(
    text,
    '''    int canceledTransitionReliabilityChangeCount(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return canceledTransitionReliabilityChanges[reason];\n    }\n\n    void clear() {\n''',
    '''    int canceledTransitionReliabilityChangeCount(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return canceledTransitionReliabilityChanges[reason];\n    }\n\n    int revertedTransitionReliabilityChangeCount(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return revertedTransitionReliabilityChanges[reason];\n    }\n\n    int supersededTransitionReliabilityChangeCount(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return supersededTransitionReliabilityChanges[reason];\n    }\n\n    static String transitionReliabilityCancellationReason(\n            String latchedLabel, String candidateLabel, String rawLabel) {\n        if (latchedLabel == null || candidateLabel == null || rawLabel == null) return "-";\n        if (rawLabel.equals(latchedLabel)) return "reverted";\n        if (!rawLabel.equals(candidateLabel)) return "superseded";\n        return "-";\n    }\n\n    void clear() {\n''',
    'reliability cancellation accessors',
)
text = replace_once(
    text,
    '''            confirmedTransitionReliabilityChanges[reason] = 0;\n            canceledTransitionReliabilityChanges[reason] = 0;\n            for (int sample = 0; sample < REVERSAL_RATE_WINDOW_SAMPLES; sample++) {\n''',
    '''            confirmedTransitionReliabilityChanges[reason] = 0;\n            canceledTransitionReliabilityChanges[reason] = 0;\n            revertedTransitionReliabilityChanges[reason] = 0;\n            supersededTransitionReliabilityChanges[reason] = 0;\n            for (int sample = 0; sample < REVERSAL_RATE_WINDOW_SAMPLES; sample++) {\n''',
    'clear reliability cancellation reasons',
)
text = replace_once(
    text,
    '''                    if (confirmedTransitionReliabilityChanges[reason] > 0\n                            || canceledTransitionReliabilityChanges[reason] > 0) {\n                        out.append(" rtr=").append(confirmedTransitionReliabilityChanges[reason])\n                                .append('/').append(canceledTransitionReliabilityChanges[reason]);\n                    }\n''',
    '''                    if (confirmedTransitionReliabilityChanges[reason] > 0\n                            || canceledTransitionReliabilityChanges[reason] > 0) {\n                        out.append(" rtr=").append(confirmedTransitionReliabilityChanges[reason])\n                                .append('/').append(canceledTransitionReliabilityChanges[reason]);\n                        if (canceledTransitionReliabilityChanges[reason] > 0) {\n                            out.append(" rcancel=").append(revertedTransitionReliabilityChanges[reason])\n                                    .append('/').append(supersededTransitionReliabilityChanges[reason]);\n                        }\n                    }\n''',
    'reliability cancellation diagnostics',
)
text = replace_once(
    text,
    '''        if (rawLabel.equals(latchedLabel)) {\n            if (candidateTransitionReliabilityLabels[reason] != null) {\n                incrementCanceledTransitionReliabilityChange(reason);\n            }\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n            return;\n        }\n        if (rawLabel.equals(candidateTransitionReliabilityLabels[reason])) {\n            candidateTransitionReliabilityStreak[reason]++;\n        } else {\n            if (candidateTransitionReliabilityLabels[reason] != null) {\n                incrementCanceledTransitionReliabilityChange(reason);\n            }\n            candidateTransitionReliabilityLabels[reason] = rawLabel;\n            candidateTransitionReliabilityStreak[reason] = 1;\n        }\n''',
    '''        String cancellationReason = transitionReliabilityCancellationReason(\n                latchedLabel, candidateTransitionReliabilityLabels[reason], rawLabel);\n        if (rawLabel.equals(latchedLabel)) {\n            if ("reverted".equals(cancellationReason)) {\n                incrementRevertedTransitionReliabilityChange(reason);\n            }\n            candidateTransitionReliabilityLabels[reason] = null;\n            candidateTransitionReliabilityStreak[reason] = 0;\n            return;\n        }\n        if (rawLabel.equals(candidateTransitionReliabilityLabels[reason])) {\n            candidateTransitionReliabilityStreak[reason]++;\n        } else {\n            if ("superseded".equals(cancellationReason)) {\n                incrementSupersededTransitionReliabilityChange(reason);\n            }\n            candidateTransitionReliabilityLabels[reason] = rawLabel;\n            candidateTransitionReliabilityStreak[reason] = 1;\n        }\n''',
    'reliability cancellation classification',
)
text = replace_once(
    text,
    '''    private void incrementCanceledTransitionReliabilityChange(int reason) {\n        if (canceledTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            canceledTransitionReliabilityChanges[reason]++;\n        }\n    }\n\n    private void updateSignalLabel(int reason) {\n''',
    '''    private void incrementRevertedTransitionReliabilityChange(int reason) {\n        if (canceledTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            canceledTransitionReliabilityChanges[reason]++;\n        }\n        if (revertedTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            revertedTransitionReliabilityChanges[reason]++;\n        }\n    }\n\n    private void incrementSupersededTransitionReliabilityChange(int reason) {\n        if (canceledTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            canceledTransitionReliabilityChanges[reason]++;\n        }\n        if (supersededTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            supersededTransitionReliabilityChanges[reason]++;\n        }\n    }\n\n    private void updateSignalLabel(int reason) {\n''',
    'reliability cancellation increments',
)
stats.write_text(text)

# Focused regression coverage.
test_path = root / "app/src/test/java/com/livecopilot/micprobe/RouteReleaseTransitionReliabilityCancelReasonTest.java"
if test_path.exists():
    raise RuntimeError(f"test file already exists: {test_path}")
test_path.write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertTrue;\n\npublic class RouteReleaseTransitionReliabilityCancelReasonTest {\n    @Test\n    public void cancellationClassifierDistinguishesRevertAndSupersede() {\n        assertEquals("reverted", RouteReleaseOutcomeStats.transitionReliabilityCancellationReason(\n                "strong", "mixed", "strong"));\n        assertEquals("superseded", RouteReleaseOutcomeStats.transitionReliabilityCancellationReason(\n                "strong", "mixed", "weak"));\n        assertEquals("-", RouteReleaseOutcomeStats.transitionReliabilityCancellationReason(\n                "strong", "mixed", "mixed"));\n        assertEquals("-", RouteReleaseOutcomeStats.transitionReliabilityCancellationReason(\n                "strong", null, "mixed"));\n    }\n\n    @Test\n    public void returnToLatchedReliabilityCountsAsRevertedCancellation() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("file-slowdown");\n\n        add(stats, "file-slowdown", "ssssr");\n        assertEquals("mixed", stats.transitionConfirmationReliabilityPendingLabel("file-slowdown"));\n        add(stats, "file-slowdown", "sr");\n\n        assertEquals(1, stats.canceledTransitionReliabilityChangeCount("file-slowdown"));\n        assertEquals(1, stats.revertedTransitionReliabilityChangeCount("file-slowdown"));\n        assertEquals(0, stats.supersededTransitionReliabilityChangeCount("file-slowdown"));\n        assertTrue(stats.diagnostics().contains("rtr=0/1 rcancel=1/0"));\n    }\n\n    @Test\n    public void confirmedTransitionDoesNotPolluteCancellationReasons() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("rt-speedup");\n\n        add(stats, "rt-speedup", "ssssr");\n        add(stats, "rt-speedup", "rrsr");\n\n        assertEquals(1, stats.confirmedTransitionReliabilityChangeCount("rt-speedup"));\n        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("rt-speedup"));\n        assertEquals(0, stats.revertedTransitionReliabilityChangeCount("rt-speedup"));\n        assertEquals(0, stats.supersededTransitionReliabilityChangeCount("rt-speedup"));\n        assertEquals(0, stats.revertedTransitionReliabilityChangeCount("unknown"));\n        assertEquals(0, stats.supersededTransitionReliabilityChangeCount("unknown"));\n    }\n\n    @Test\n    public void clearResetsReliabilityCancellationBreakdown() {\n        RouteReleaseOutcomeStats stats = stronglyConfirmed("file-speedup");\n        add(stats, "file-speedup", "ssssr");\n        add(stats, "file-speedup", "sr");\n        assertEquals(1, stats.revertedTransitionReliabilityChangeCount("file-speedup"));\n\n        stats.clear();\n\n        assertEquals(0, stats.canceledTransitionReliabilityChangeCount("file-speedup"));\n        assertEquals(0, stats.revertedTransitionReliabilityChangeCount("file-speedup"));\n        assertEquals(0, stats.supersededTransitionReliabilityChangeCount("file-speedup"));\n        assertEquals("", stats.diagnostics());\n    }\n\n    private static RouteReleaseOutcomeStats stronglyConfirmed(String reason) {\n        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();\n        add(stats, reason, "sssrrsrrr");\n        assertEquals("strong", stats.transitionConfirmationReliabilityLabel(reason));\n        return stats;\n    }\n\n    private static void add(RouteReleaseOutcomeStats stats, String reason, String sequence) {\n        for (int i = 0; i < sequence.length(); i++) {\n            char sample = sequence.charAt(i);\n            stats.record(reason, sample == 'r' ? "reversal" : "stable");\n        }\n    }\n}\n''')
