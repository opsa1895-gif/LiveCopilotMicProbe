from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one match in {path}, got {text.count(old)}")
    p.write_text(text.replace(old, new, 1))


policy = '''package com.livecopilot.micprobe;

final class RealtimeRoutingPolicy {
    static final long SECOND_UNSTABLE_BLOCK_MS = 3_000L;
    static final long THIRD_UNSTABLE_BLOCK_MS = 8_000L;
    static final long MAX_UNSTABLE_BLOCK_MS = 15_000L;
    static final long FIRST_OUTCOME_BLOCK_MS = 2_000L;
    static final long SECOND_OUTCOME_BLOCK_MS = 5_000L;
    static final long THIRD_OUTCOME_BLOCK_MS = 10_000L;
    static final long MAX_OUTCOME_BLOCK_MS = 15_000L;
    static final long BAD_FILE_PROBE_MS = 1_000L;
    static final int MAX_OUTCOME_PENALTY = 4;

    private RealtimeRoutingPolicy() {}

    static boolean isQuickFailure(long readyAtMs, long failedAtMs, long stableWindowMs) {
        return readyAtMs > 0L
                && failedAtMs >= readyAtMs
                && stableWindowMs > 0L
                && failedAtMs - readyAtMs < stableWindowMs;
    }

    static long blockMsForUnstableStreak(int unstableStreak) {
        if (unstableStreak < 2) return 0L;
        if (unstableStreak == 2) return SECOND_UNSTABLE_BLOCK_MS;
        if (unstableStreak == 3) return THIRD_UNSTABLE_BLOCK_MS;
        return MAX_UNSTABLE_BLOCK_MS;
    }

    static int nextOutcomePenalty(int currentPenalty, boolean acceptedUseful,
                                  boolean fileRecovery) {
        int safe = Math.max(0, Math.min(MAX_OUTCOME_PENALTY, currentPenalty));
        if (fileRecovery) {
            int delta = acceptedUseful ? 1 : 2;
            return Math.min(MAX_OUTCOME_PENALTY, safe + delta);
        }
        if (!acceptedUseful) {
            return Math.min(MAX_OUTCOME_PENALTY, safe + 1);
        }
        return Math.max(0, safe - 2);
    }

    static long blockMsForOutcomePenalty(int penalty) {
        if (penalty <= 0) return 0L;
        if (penalty == 1) return FIRST_OUTCOME_BLOCK_MS;
        if (penalty == 2) return SECOND_OUTCOME_BLOCK_MS;
        if (penalty == 3) return THIRD_OUTCOME_BLOCK_MS;
        return MAX_OUTCOME_BLOCK_MS;
    }

    static int penaltyAfterBadFile(int currentPenalty) {
        return Math.max(0, Math.min(MAX_OUTCOME_PENALTY, currentPenalty) - 1);
    }

    static long shortenBlockAfterBadFile(long nowMs, long blockedUntilMs) {
        if (blockedUntilMs <= nowMs) return blockedUntilMs;
        return Math.min(blockedUntilMs, nowMs + BAD_FILE_PROBE_MS);
    }

    static boolean isUsableFileOutcome(boolean hasFocus, int submittedChunks,
                                       int failedChunks, boolean lastChunkFailed) {
        return hasFocus
                && submittedChunks > 0
                && !lastChunkFailed
                && FileTurnCoveragePolicy.coveragePercent(submittedChunks, failedChunks)
                >= FileTurnCoveragePolicy.MIN_CONSERVATIVE_COVERAGE_PERCENT;
    }

    static boolean shouldUseRealtime(boolean ready, boolean socketPresent,
                                     long nowMs, long blockedUntilMs) {
        return ready
                && socketPresent
                && (blockedUntilMs <= 0L || nowMs >= blockedUntilMs);
    }
}
'''
Path('app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java').write_text(policy)

# Realtime client: keep transport instability and transcript outcome health separate.
replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
    '''    private long readyAtMs;\n    private int unstableReadyFailureStreak;\n    private long routingBlockedUntilMs;\n    private long latestRecoveryId;''',
    '''    private long readyAtMs;\n    private int unstableReadyFailureStreak;\n    private int routingOutcomePenalty;\n    private long routingBlockedUntilMs;\n    private long latestRecoveryId;''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
    '''        readyAtMs = 0L;\n        unstableReadyFailureStreak = 0;\n        routingBlockedUntilMs = 0L;''',
    '''        readyAtMs = 0L;\n        unstableReadyFailureStreak = 0;\n        routingOutcomePenalty = 0;\n        routingBlockedUntilMs = 0L;''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java',
    '''    synchronized int unstableReadyFailureStreak() {\n        return Math.max(0, unstableReadyFailureStreak);\n    }\n\n    synchronized void shutdown() {''',
    '''    synchronized int unstableReadyFailureStreak() {\n        return Math.max(0, unstableReadyFailureStreak);\n    }\n\n    synchronized int routingOutcomePenalty() {\n        return Math.max(0, routingOutcomePenalty);\n    }\n\n    synchronized void noteRealtimeTranscriptOutcome(boolean acceptedUseful, boolean fileRecovery) {\n        if (closed || !wanted) return;\n        routingOutcomePenalty = RealtimeRoutingPolicy.nextOutcomePenalty(\n                routingOutcomePenalty, acceptedUseful, fileRecovery);\n        if (acceptedUseful && !fileRecovery) return;\n\n        long blockMs = RealtimeRoutingPolicy.blockMsForOutcomePenalty(routingOutcomePenalty);\n        if (blockMs <= 0L) return;\n        long now = System.currentTimeMillis();\n        routingBlockedUntilMs = Math.max(routingBlockedUntilMs, now + blockMs);\n    }\n\n    synchronized void notePrimaryFileTurnOutcome(boolean usable) {\n        if (closed || !wanted || usable) return;\n        long now = System.currentTimeMillis();\n        routingOutcomePenalty = RealtimeRoutingPolicy.penaltyAfterBadFile(\n                routingOutcomePenalty);\n        routingBlockedUntilMs = RealtimeRoutingPolicy.shortenBlockAfterBadFile(\n                now, routingBlockedUntilMs);\n    }\n\n    synchronized void shutdown() {''')

# Service: feed accepted/rejected STT outcomes back into routing health.
replace_once(
    'app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java',
    '''        if (TranscriptQualityPolicy.isLowQuality(clean)) {\n            aiStatus = "Слушам";''',
    '''        if (TranscriptQualityPolicy.isLowQuality(clean)) {\n            noteRoutingTranscriptOutcome(source, false);\n            aiStatus = "Слушам";''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java',
    '''        if (!useful.isEmpty()) {\n            recordAcceptedSttSource(source);\n            semanticEpoch++;''',
    '''        if (!useful.isEmpty()) {\n            noteRoutingTranscriptOutcome(source, true);\n            recordAcceptedSttSource(source);\n            semanticEpoch++;''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java',
    '''    private void recordAcceptedSttSource(AcceptedSttSource source) {''',
    '''    private void noteRoutingTranscriptOutcome(AcceptedSttSource source, boolean acceptedUseful) {\n        if (realtimeTranscriber == null || source == null) return;\n        if (source == AcceptedSttSource.REALTIME) {\n            realtimeTranscriber.noteRealtimeTranscriptOutcome(acceptedUseful, false);\n        } else if (source == AcceptedSttSource.REALTIME_FILE_RECOVERY) {\n            realtimeTranscriber.noteRealtimeTranscriptOutcome(acceptedUseful, true);\n        }\n    }\n\n    private void recordAcceptedSttSource(AcceptedSttSource source) {''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java',
    '''        lastFileDegradedStreak = Math.max(0, degradedTurnStreak);\n        long safeCooldownMs = Math.max(0L, retryCooldownRemainingMs);''',
    '''        lastFileDegradedStreak = Math.max(0, degradedTurnStreak);\n        boolean usableFileRoute = RealtimeRoutingPolicy.isUsableFileOutcome(\n                hasText(turnFocus), submittedChunks, failedChunks, lastChunkFailed);\n        if (realtimeTranscriber != null) {\n            realtimeTranscriber.notePrimaryFileTurnOutcome(usableFileRoute);\n        }\n        long safeCooldownMs = Math.max(0L, retryCooldownRemainingMs);''')

replace_once(
    'app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java',
    '''        int unstableRt = realtimeTranscriber == null\n                ? 0 : realtimeTranscriber.unstableReadyFailureStreak();\n        String rt = " • RT " + realtimeState\n                + (realtimeStateSerial > 0L ? "@" + realtimeStateSerial : "")\n                + (unstableRt > 0 ? " • unstable×" + unstableRt : "")\n                + (routeBlockMs > 0L ? " • route-cd " + latencyLabel(routeBlockMs) : "");''',
    '''        int unstableRt = realtimeTranscriber == null\n                ? 0 : realtimeTranscriber.unstableReadyFailureStreak();\n        int routeOutcomePenalty = realtimeTranscriber == null\n                ? 0 : realtimeTranscriber.routingOutcomePenalty();\n        String rt = " • RT " + realtimeState\n                + (realtimeStateSerial > 0L ? "@" + realtimeStateSerial : "")\n                + (unstableRt > 0 ? " • unstable×" + unstableRt : "")\n                + (routeOutcomePenalty > 0 ? " • outcome×" + routeOutcomePenalty : "")\n                + (routeBlockMs > 0L ? " • route-cd " + latencyLabel(routeBlockMs) : "");''')

# Version bump.
replace_once(
    'app/build.gradle.kts',
    '''        versionCode = 68\n        versionName = "0.67.0-realtime-route-hysteresis"''',
    '''        versionCode = 69\n        versionName = "0.68.0-adaptive-stt-routing"''')

test = '''package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeRoutingPolicyTest {
    @Test
    public void quickFailureMustHappenInsideStableWindow() {
        assertTrue(RealtimeRoutingPolicy.isQuickFailure(1_000L, 5_000L, 15_000L));
        assertFalse(RealtimeRoutingPolicy.isQuickFailure(1_000L, 16_000L, 15_000L));
        assertFalse(RealtimeRoutingPolicy.isQuickFailure(0L, 5_000L, 15_000L));
        assertFalse(RealtimeRoutingPolicy.isQuickFailure(5_000L, 4_000L, 15_000L));
    }

    @Test
    public void repeatedUnstableConnectionsIncreaseRoutingBlock() {
        assertEquals(0L, RealtimeRoutingPolicy.blockMsForUnstableStreak(0));
        assertEquals(0L, RealtimeRoutingPolicy.blockMsForUnstableStreak(1));
        assertEquals(3_000L, RealtimeRoutingPolicy.blockMsForUnstableStreak(2));
        assertEquals(8_000L, RealtimeRoutingPolicy.blockMsForUnstableStreak(3));
        assertEquals(15_000L, RealtimeRoutingPolicy.blockMsForUnstableStreak(4));
        assertEquals(15_000L, RealtimeRoutingPolicy.blockMsForUnstableStreak(9));
    }

    @Test
    public void transcriptOutcomesBuildAndRecoverPenalty() {
        int penalty = 0;
        penalty = RealtimeRoutingPolicy.nextOutcomePenalty(penalty, false, false);
        assertEquals(1, penalty);
        penalty = RealtimeRoutingPolicy.nextOutcomePenalty(penalty, true, true);
        assertEquals(2, penalty);
        penalty = RealtimeRoutingPolicy.nextOutcomePenalty(penalty, false, true);
        assertEquals(4, penalty);
        penalty = RealtimeRoutingPolicy.nextOutcomePenalty(penalty, true, false);
        assertEquals(2, penalty);
        penalty = RealtimeRoutingPolicy.nextOutcomePenalty(penalty, true, false);
        assertEquals(0, penalty);
    }

    @Test
    public void transcriptOutcomePenaltyUsesBoundedHold() {
        assertEquals(0L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(0));
        assertEquals(2_000L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(1));
        assertEquals(5_000L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(2));
        assertEquals(10_000L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(3));
        assertEquals(15_000L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(4));
        assertEquals(15_000L, RealtimeRoutingPolicy.blockMsForOutcomePenalty(9));
    }

    @Test
    public void badFileOutcomeAcceleratesRealtimeProbe() {
        assertEquals(2, RealtimeRoutingPolicy.penaltyAfterBadFile(3));
        assertEquals(0, RealtimeRoutingPolicy.penaltyAfterBadFile(0));
        assertEquals(11_000L,
                RealtimeRoutingPolicy.shortenBlockAfterBadFile(10_000L, 25_000L));
        assertEquals(9_000L,
                RealtimeRoutingPolicy.shortenBlockAfterBadFile(10_000L, 9_000L));
    }

    @Test
    public void fileOutcomeNeedsUsefulFocusAndCoverage() {
        assertTrue(RealtimeRoutingPolicy.isUsableFileOutcome(true, 4, 1, false));
        assertFalse(RealtimeRoutingPolicy.isUsableFileOutcome(true, 3, 2, false));
        assertFalse(RealtimeRoutingPolicy.isUsableFileOutcome(true, 3, 0, true));
        assertFalse(RealtimeRoutingPolicy.isUsableFileOutcome(false, 3, 0, false));
        assertFalse(RealtimeRoutingPolicy.isUsableFileOutcome(true, 0, 0, false));
    }

    @Test
    public void connectedSocketIsRoutableOnlyAfterBlockExpires() {
        assertTrue(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 10_000L, 0L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 10_000L, 12_000L));
        assertTrue(RealtimeRoutingPolicy.shouldUseRealtime(true, true, 12_000L, 12_000L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(false, true, 12_000L, 0L));
        assertFalse(RealtimeRoutingPolicy.shouldUseRealtime(true, false, 12_000L, 0L));
    }
}
'''
Path('app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingPolicyTest.java').write_text(test)
