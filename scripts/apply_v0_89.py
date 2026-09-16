from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# Version bump.
path = Path("app/build.gradle.kts")
text = path.read_text()
text = replace_once(
    text,
    '        versionCode = 89\n        versionName = "0.88.0-regime-release-outcomes"',
    '        versionCode = 90\n        versionName = "0.89.0-regime-release-breakdown"',
    "version",
)
path.write_text(text)


# Replace aggregate release outcome counters with reason-aware local stats.
path = Path("app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java")
text = path.read_text()
text = replace_once(
    text,
    '    private int routeFlapReleaseStableCount;\n'
    '    private int routeFlapReleaseReversalCount;\n'
    '    private int routeFlapReleaseExpiredCount;',
    '    private final RouteReleaseOutcomeStats routeFlapReleaseOutcomeStats =\n'
    '            new RouteReleaseOutcomeStats();',
    "release outcome fields",
)
text = replace_once(
    text,
    '        routeFlapReleaseStableCount = 0;\n'
    '        routeFlapReleaseReversalCount = 0;\n'
    '        routeFlapReleaseExpiredCount = 0;',
    '        routeFlapReleaseOutcomeStats.clear();',
    "release outcome reset",
)
text = replace_once(
    text,
    '            lastRouteFlapReleaseStableStreak = 0;\n'
    '            lastRouteFlapReleaseOutcome = outcome;\n'
    '            routeFlapReleaseExpiredCount++;\n'
    '            return;',
    '            lastRouteFlapReleaseStableStreak = 0;\n'
    '            lastRouteFlapReleaseOutcome = outcome;\n'
    '            routeFlapReleaseOutcomeStats.record(lastRouteFlapReleaseReason, outcome);\n'
    '            return;',
    "expired outcome accounting",
)
text = replace_once(
    text,
    '            lastRouteFlapReleaseStableStreak = 0;\n'
    '            lastRouteFlapReleaseOutcome = outcome;\n'
    '            routeFlapReleaseReversalCount++;\n'
    '            return;',
    '            lastRouteFlapReleaseStableStreak = 0;\n'
    '            lastRouteFlapReleaseOutcome = outcome;\n'
    '            routeFlapReleaseOutcomeStats.record(lastRouteFlapReleaseReason, outcome);\n'
    '            return;',
    "reversal outcome accounting",
)
text = replace_once(
    text,
    '        lastRouteFlapReleaseOutcome = outcome;\n'
    '        if ("stable".equals(outcome)) routeFlapReleaseStableCount++;',
    '        lastRouteFlapReleaseOutcome = outcome;\n'
    '        if ("stable".equals(outcome)) {\n'
    '            routeFlapReleaseOutcomeStats.record(lastRouteFlapReleaseReason, outcome);\n'
    '        }',
    "stable outcome accounting",
)
text = replace_once(
    text,
    '        if (routeFlapReleaseStableCount > 0 || routeFlapReleaseReversalCount > 0\n'
    '                || routeFlapReleaseExpiredCount > 0) {\n'
    '            out.append(" • rel s").append(routeFlapReleaseStableCount)\n'
    '                    .append("/r").append(routeFlapReleaseReversalCount)\n'
    '                    .append("/x").append(routeFlapReleaseExpiredCount);\n'
    '        }',
    '        String releaseBreakdown = routeFlapReleaseOutcomeStats.diagnostics();\n'
    '        if (!releaseBreakdown.isEmpty()) {\n'
    '            out.append(" • ").append(releaseBreakdown);\n'
    '        }',
    "release diagnostics breakdown",
)
path.write_text(text)


stats = '''package com.livecopilot.micprobe;

final class RouteReleaseOutcomeStats {
    private static final int REASON_RT_SLOWDOWN = 0;
    private static final int REASON_RT_SPEEDUP = 1;
    private static final int REASON_FILE_SPEEDUP = 2;
    private static final int REASON_FILE_SLOWDOWN = 3;
    private static final int REASON_COUNT = 4;

    private static final int OUTCOME_STABLE = 0;
    private static final int OUTCOME_REVERSAL = 1;
    private static final int OUTCOME_EXPIRED = 2;
    private static final int OUTCOME_COUNT = 3;

    private final int[][] counts = new int[REASON_COUNT][OUTCOME_COUNT];

    void record(String releaseReason, String outcome) {
        int reason = reasonIndex(releaseReason);
        int result = outcomeIndex(outcome);
        if (reason < 0 || result < 0) return;
        if (counts[reason][result] < Integer.MAX_VALUE) counts[reason][result]++;
    }

    int count(String releaseReason, String outcome) {
        int reason = reasonIndex(releaseReason);
        int result = outcomeIndex(outcome);
        if (reason < 0 || result < 0) return 0;
        return counts[reason][result];
    }

    void clear() {
        for (int reason = 0; reason < REASON_COUNT; reason++) {
            for (int outcome = 0; outcome < OUTCOME_COUNT; outcome++) {
                counts[reason][outcome] = 0;
            }
        }
    }

    boolean isEmpty() {
        for (int reason = 0; reason < REASON_COUNT; reason++) {
            for (int outcome = 0; outcome < OUTCOME_COUNT; outcome++) {
                if (counts[reason][outcome] > 0) return false;
            }
        }
        return true;
    }

    String diagnostics() {
        if (isEmpty()) return "";
        StringBuilder out = new StringBuilder("rel s/r/x");
        for (int reason = 0; reason < REASON_COUNT; reason++) {
            if (reasonTotal(reason) <= 0) continue;
            out.append(' ').append(reasonLabel(reason)).append(' ')
                    .append(counts[reason][OUTCOME_STABLE]).append('/')
                    .append(counts[reason][OUTCOME_REVERSAL]).append('/')
                    .append(counts[reason][OUTCOME_EXPIRED]);
        }
        return out.toString();
    }

    private int reasonTotal(int reason) {
        return counts[reason][OUTCOME_STABLE]
                + counts[reason][OUTCOME_REVERSAL]
                + counts[reason][OUTCOME_EXPIRED];
    }

    private static int reasonIndex(String reason) {
        if ("rt-slowdown".equals(reason)) return REASON_RT_SLOWDOWN;
        if ("rt-speedup".equals(reason)) return REASON_RT_SPEEDUP;
        if ("file-speedup".equals(reason)) return REASON_FILE_SPEEDUP;
        if ("file-slowdown".equals(reason)) return REASON_FILE_SLOWDOWN;
        return -1;
    }

    private static String reasonLabel(int reason) {
        if (reason == REASON_RT_SLOWDOWN) return "rtslow";
        if (reason == REASON_RT_SPEEDUP) return "rtfast";
        if (reason == REASON_FILE_SPEEDUP) return "ffast";
        if (reason == REASON_FILE_SLOWDOWN) return "fslow";
        return "?";
    }

    private static int outcomeIndex(String outcome) {
        if ("stable".equals(outcome)) return OUTCOME_STABLE;
        if ("reversal".equals(outcome)) return OUTCOME_REVERSAL;
        if ("expired".equals(outcome)) return OUTCOME_EXPIRED;
        return -1;
    }
}
'''
Path("app/src/main/java/com/livecopilot/micprobe/RouteReleaseOutcomeStats.java").write_text(stats)


test = '''package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteReleaseOutcomeStatsTest {
    @Test
    public void tracksOutcomesIndependentlyForEveryReleaseReason() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "stable");
        stats.record("rt-slowdown", "reversal");
        stats.record("rt-speedup", "reversal");
        stats.record("file-speedup", "expired");
        stats.record("file-slowdown", "stable");

        assertEquals(1, stats.count("rt-slowdown", "stable"));
        assertEquals(1, stats.count("rt-slowdown", "reversal"));
        assertEquals(1, stats.count("rt-speedup", "reversal"));
        assertEquals(1, stats.count("file-speedup", "expired"));
        assertEquals(1, stats.count("file-slowdown", "stable"));
        assertEquals(0, stats.count("file-slowdown", "reversal"));
    }

    @Test
    public void pendingAndUnknownSignalsDoNotPolluteCompletedOutcomeCounts() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "pending");
        stats.record("unknown", "stable");
        stats.record("file-speedup", "unknown");

        assertTrue(stats.isEmpty());
        assertEquals("", stats.diagnostics());
    }

    @Test
    public void diagnosticsShowOnlyReasonsSeenInThisSession() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-slowdown", "stable");
        stats.record("rt-slowdown", "reversal");
        stats.record("file-slowdown", "expired");

        assertEquals("rel s/r/x rtslow 1/1/0 fslow 0/0/1", stats.diagnostics());
    }

    @Test
    public void clearResetsAllReasonBuckets() {
        RouteReleaseOutcomeStats stats = new RouteReleaseOutcomeStats();
        stats.record("rt-speedup", "stable");
        stats.record("file-speedup", "reversal");
        stats.clear();

        assertTrue(stats.isEmpty());
        assertEquals(0, stats.count("rt-speedup", "stable"));
        assertEquals(0, stats.count("file-speedup", "reversal"));
    }
}
'''
Path("app/src/test/java/com/livecopilot/micprobe/RouteReleaseOutcomeStatsTest.java").write_text(test)
