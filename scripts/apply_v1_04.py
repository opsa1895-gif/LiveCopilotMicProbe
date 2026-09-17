from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


root = Path(__file__).resolve().parents[1]

gradle = root / "app/build.gradle.kts"
text = gradle.read_text()
text = replace_once(text, 'versionCode = 104', 'versionCode = 105', 'versionCode')
text = replace_once(
    text,
    'versionName = "1.03.0-reliability-transition-confirmation-rate"',
    'versionName = "1.04.0-recent-reliability-transition-rate"',
    'versionName',
)
gradle.write_text(text)

window = root / "app/src/main/java/com/livecopilot/micprobe/RecentConfirmationRateWindow.java"
if window.exists():
    raise RuntimeError(f"window file already exists: {window}")
window.write_text('''package com.livecopilot.micprobe;\n\nimport java.util.Arrays;\n\nfinal class RecentConfirmationRateWindow {\n    private final int[] outcomes;\n    private int next;\n    private int size;\n    private int confirmations;\n\n    RecentConfirmationRateWindow(int capacity) {\n        if (capacity <= 0) throw new IllegalArgumentException("capacity");\n        outcomes = new int[capacity];\n    }\n\n    void recordConfirmed() {\n        record(true);\n    }\n\n    void recordReverted() {\n        record(false);\n    }\n\n    int sampleCount() {\n        return size;\n    }\n\n    int confirmationRatePercent(int minimumSamples) {\n        if (minimumSamples <= 0) throw new IllegalArgumentException("minimumSamples");\n        if (size < minimumSamples) return -1;\n        return (int) (((long) confirmations * 100L) / size);\n    }\n\n    void clear() {\n        Arrays.fill(outcomes, 0);\n        next = 0;\n        size = 0;\n        confirmations = 0;\n    }\n\n    private void record(boolean confirmed) {\n        int value = confirmed ? 1 : 0;\n        if (size == outcomes.length) {\n            confirmations -= outcomes[next];\n        } else {\n            size++;\n        }\n        outcomes[next] = value;\n        confirmations += value;\n        next = (next + 1) % outcomes.length;\n    }\n}\n''')

stats = root / "app/src/main/java/com/livecopilot/micprobe/RouteReleaseOutcomeStats.java"
text = stats.read_text()
text = replace_once(
    text,
    '    static final int MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES = 2;\n',
    '    static final int MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES = 2;\n'
    '    static final int RELIABILITY_TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES = 8;\n',
    'recent reliability transition rate window constant',
)
text = replace_once(
    text,
    '''    private final int[] supersededTransitionReliabilityChanges = new int[REASON_COUNT];\n\n    void record(String releaseReason, String outcome) {\n''',
    '''    private final int[] supersededTransitionReliabilityChanges = new int[REASON_COUNT];\n    private final RecentConfirmationRateWindow[] recentReliabilityTransitionRates =\n            new RecentConfirmationRateWindow[REASON_COUNT];\n\n    RouteReleaseOutcomeStats() {\n        for (int reason = 0; reason < REASON_COUNT; reason++) {\n            recentReliabilityTransitionRates[reason] = new RecentConfirmationRateWindow(\n                    RELIABILITY_TRANSITION_CONFIRMATION_RATE_WINDOW_SAMPLES);\n        }\n    }\n\n    void record(String releaseReason, String outcome) {\n''',
    'recent reliability transition rate fields and constructor',
)
text = replace_once(
    text,
    '''    int reliabilityTransitionConfirmationRateSampleCount(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return reliabilityTransitionConfirmationRateSampleCountForCounts(\n                confirmedTransitionReliabilityChanges[reason],\n                revertedTransitionReliabilityChanges[reason]);\n    }\n\n    int reliabilityTransitionConfirmationRatePercent(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return -1;\n        return reliabilityTransitionConfirmationRatePercentForCounts(\n                confirmedTransitionReliabilityChanges[reason],\n                revertedTransitionReliabilityChanges[reason]);\n    }\n''',
    '''    int reliabilityTransitionConfirmationRateSampleCount(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return 0;\n        return recentReliabilityTransitionRates[reason].sampleCount();\n    }\n\n    int reliabilityTransitionConfirmationRatePercent(String releaseReason) {\n        int reason = reasonIndex(releaseReason);\n        if (reason < 0) return -1;\n        return recentReliabilityTransitionRates[reason].confirmationRatePercent(\n                MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES);\n    }\n''',
    'runtime reliability transition rate accessors',
)
text = replace_once(
    text,
    '''            supersededTransitionReliabilityChanges[reason] = 0;\n            for (int sample = 0; sample < REVERSAL_RATE_WINDOW_SAMPLES; sample++) {\n''',
    '''            supersededTransitionReliabilityChanges[reason] = 0;\n            recentReliabilityTransitionRates[reason].clear();\n            for (int sample = 0; sample < REVERSAL_RATE_WINDOW_SAMPLES; sample++) {\n''',
    'clear recent reliability transition rate',
)
text = replace_once(
    text,
    '''                        int reliabilityTransitionSamples =\n                                reliabilityTransitionConfirmationRateSampleCountForCounts(\n                                        confirmedTransitionReliabilityChanges[reason],\n                                        revertedTransitionReliabilityChanges[reason]);\n                        if (reliabilityTransitionSamples\n                                >= MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) {\n                            out.append(" rconf")\n                                    .append(reliabilityTransitionConfirmationRatePercentForCounts(\n                                            confirmedTransitionReliabilityChanges[reason],\n                                            revertedTransitionReliabilityChanges[reason]))\n                                    .append("%@").append(reliabilityTransitionSamples);\n                        }\n''',
    '''                        int reliabilityTransitionSamples =\n                                recentReliabilityTransitionRates[reason].sampleCount();\n                        if (reliabilityTransitionSamples\n                                >= MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES) {\n                            out.append(" rconf")\n                                    .append(recentReliabilityTransitionRates[reason]\n                                            .confirmationRatePercent(\n                                                    MIN_RELIABILITY_TRANSITION_CONFIRMATION_RATE_SAMPLES))\n                                    .append("%@").append(reliabilityTransitionSamples);\n                        }\n''',
    'recent reliability transition rate diagnostics',
)
text = replace_once(
    text,
    '''    private void incrementConfirmedTransitionReliabilityChange(int reason) {\n        if (confirmedTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            confirmedTransitionReliabilityChanges[reason]++;\n        }\n    }\n\n    private void incrementRevertedTransitionReliabilityChange(int reason) {\n        if (canceledTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            canceledTransitionReliabilityChanges[reason]++;\n        }\n        if (revertedTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            revertedTransitionReliabilityChanges[reason]++;\n        }\n    }\n''',
    '''    private void incrementConfirmedTransitionReliabilityChange(int reason) {\n        if (confirmedTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            confirmedTransitionReliabilityChanges[reason]++;\n        }\n        recentReliabilityTransitionRates[reason].recordConfirmed();\n    }\n\n    private void incrementRevertedTransitionReliabilityChange(int reason) {\n        if (canceledTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            canceledTransitionReliabilityChanges[reason]++;\n        }\n        if (revertedTransitionReliabilityChanges[reason] < Integer.MAX_VALUE) {\n            revertedTransitionReliabilityChanges[reason]++;\n        }\n        recentReliabilityTransitionRates[reason].recordReverted();\n    }\n''',
    'record recent reliability transition resolutions',
)
stats.write_text(text)

existing_test = root / "app/src/test/java/com/livecopilot/micprobe/RouteReleaseTransitionReliabilityConfirmationRateTest.java"
text = existing_test.read_text()
text = replace_once(
    text,
    '''    @Test\n    public void negativeSyntheticCountsAreClampedForHelperSafety() {\n''',
    '''    @Test\n    public void confirmedAndRevertedChangesFeedRecentRuntimeRate() {\n        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();\n        add(stats, "rt-speedup", "srsrsrsrssrsssr");\n\n        assertEquals(1, stats.confirmedTransitionReliabilityChangeCount("rt-speedup"));\n        assertEquals(1, stats.revertedTransitionReliabilityChangeCount("rt-speedup"));\n        assertEquals(2, stats.reliabilityTransitionConfirmationRateSampleCount("rt-speedup"));\n        assertEquals(50, stats.reliabilityTransitionConfirmationRatePercent("rt-speedup"));\n    }\n\n    @Test\n    public void negativeSyntheticCountsAreClampedForHelperSafety() {\n''',
    'runtime recent rate integration test',
)
existing_test.write_text(text)

window_test = root / "app/src/test/java/com/livecopilot/micprobe/RecentConfirmationRateWindowTest.java"
if window_test.exists():
    raise RuntimeError(f"window test already exists: {window_test}")
window_test.write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertEquals;\n\npublic class RecentConfirmationRateWindowTest {\n    @Test\n    public void rateIsSuppressedUntilMinimumSamples() {\n        RecentConfirmationRateWindow window = new RecentConfirmationRateWindow(8);\n\n        window.recordConfirmed();\n        assertEquals(1, window.sampleCount());\n        assertEquals(-1, window.confirmationRatePercent(2));\n\n        window.recordReverted();\n        assertEquals(2, window.sampleCount());\n        assertEquals(50, window.confirmationRatePercent(2));\n    }\n\n    @Test\n    public void oldestResolutionsAgeOutOfEightSampleWindow() {\n        RecentConfirmationRateWindow window = new RecentConfirmationRateWindow(8);\n        for (int i = 0; i < 8; i++) window.recordConfirmed();\n\n        assertEquals(8, window.sampleCount());\n        assertEquals(100, window.confirmationRatePercent(2));\n\n        for (int i = 0; i < 4; i++) window.recordReverted();\n        assertEquals(8, window.sampleCount());\n        assertEquals(50, window.confirmationRatePercent(2));\n\n        for (int i = 0; i < 4; i++) window.recordReverted();\n        assertEquals(8, window.sampleCount());\n        assertEquals(0, window.confirmationRatePercent(2));\n    }\n\n    @Test\n    public void clearResetsWindowState() {\n        RecentConfirmationRateWindow window = new RecentConfirmationRateWindow(8);\n        for (int i = 0; i < 5; i++) window.recordConfirmed();\n        for (int i = 0; i < 3; i++) window.recordReverted();\n\n        window.clear();\n\n        assertEquals(0, window.sampleCount());\n        assertEquals(-1, window.confirmationRatePercent(2));\n        window.recordReverted();\n        window.recordReverted();\n        assertEquals(0, window.confirmationRatePercent(2));\n    }\n\n    @Test(expected = IllegalArgumentException.class)\n    public void zeroCapacityIsRejected() {\n        new RecentConfirmationRateWindow(0);\n    }\n}\n''')
