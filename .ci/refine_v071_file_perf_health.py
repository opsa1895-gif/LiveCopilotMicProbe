from pathlib import Path

ROOT = Path('.')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 match, found {count}')
    return text.replace(old, new, 1)

policy = ROOT / 'app/src/main/java/com/livecopilot/micprobe/RealtimeRoutingPolicy.java'
text = policy.read_text()
text = replace_once(text,
'''    static boolean isUsableFileOutcome(boolean hasFocus, int submittedChunks,\n                                       int failedChunks, boolean lastChunkFailed) {\n        return hasFocus\n                && submittedChunks > 0\n                && !lastChunkFailed\n                && FileTurnCoveragePolicy.coveragePercent(submittedChunks, failedChunks)\n                >= FileTurnCoveragePolicy.MIN_CONSERVATIVE_COVERAGE_PERCENT;\n    }\n\n    static boolean shouldUseRealtime(boolean ready, boolean socketPresent,\n''',
'''    static boolean isUsableFileOutcome(boolean hasFocus, int submittedChunks,\n                                       int failedChunks, boolean lastChunkFailed) {\n        return hasFocus\n                && submittedChunks > 0\n                && !lastChunkFailed\n                && FileTurnCoveragePolicy.coveragePercent(submittedChunks, failedChunks)\n                >= FileTurnCoveragePolicy.MIN_CONSERVATIVE_COVERAGE_PERCENT;\n    }\n\n    static boolean isFilePerformanceEligible(boolean usable, int degradedTurnStreak,\n                                             long retryCooldownRemainingMs) {\n        return usable && degradedTurnStreak <= 0 && retryCooldownRemainingMs <= 0L;\n    }\n\n    static boolean shouldUseRealtime(boolean ready, boolean socketPresent,\n''', 'policy file performance eligibility')
policy.write_text(text)

client = ROOT / 'app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java'
text = client.read_text()
old = '''    synchronized void notePrimaryFileTurnOutcome(boolean usable, long drainLatencyMs) {\n        if (closed || !wanted) return;\n        goodRealtimeStreak = 0;\n        long now = System.currentTimeMillis();\n        if (usable) {\n            if (drainLatencyMs >= 0L) {\n                fileRouteLatencyEstimateMs = RealtimeRoutingPolicy.nextRouteLatencyEstimate(\n                        fileRouteLatencyEstimateMs, fileRouteLatencySamples, drainLatencyMs);\n                fileRouteLatencySamples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(\n                        fileRouteLatencySamples, drainLatencyMs);\n                fileRouteLatencySampleAtMs = now;\n            }\n            return;\n        }\n        // A degraded file turn invalidates any old "file is faster" evidence so an\n        // unhealthy file lane cannot keep winning on stale performance history.\n        fileRouteLatencyEstimateMs = -1L;\n        fileRouteLatencySamples = 0;\n        fileRouteLatencySampleAtMs = 0L;\n        decayRoutingPenaltiesLocked(now);\n        routingQualityPenalty = RealtimeRoutingPolicy.penaltyAfterBadFile(\n                routingQualityPenalty);\n        routingLatencyPenalty = RealtimeRoutingPolicy.penaltyAfterBadFile(\n                routingLatencyPenalty);\n        goodRealtimeStreak = 0;\n        lastRoutingSignalAtMs = now;\n        outcomeBlockedUntilMs = RealtimeRoutingPolicy.shortenOutcomeBlockAfterBadFile(\n                now, outcomeBlockedUntilMs);\n    }\n'''
new = '''    synchronized void notePrimaryFileTurnOutcome(boolean usable, boolean performanceEligible,\n                                                 long drainLatencyMs) {\n        if (closed || !wanted) return;\n        goodRealtimeStreak = 0;\n        long now = System.currentTimeMillis();\n        if (performanceEligible && drainLatencyMs >= 0L) {\n            fileRouteLatencyEstimateMs = RealtimeRoutingPolicy.nextRouteLatencyEstimate(\n                    fileRouteLatencyEstimateMs, fileRouteLatencySamples, drainLatencyMs);\n            fileRouteLatencySamples = RealtimeRoutingPolicy.nextRouteLatencySampleCount(\n                    fileRouteLatencySamples, drainLatencyMs);\n            fileRouteLatencySampleAtMs = now;\n        } else if (!performanceEligible) {\n            // A degraded/cooldown file lane cannot keep winning on stale speed history,\n            // even if its transcript coverage was still usable enough for context.\n            fileRouteLatencyEstimateMs = -1L;\n            fileRouteLatencySamples = 0;\n            fileRouteLatencySampleAtMs = 0L;\n        }\n        if (usable) return;\n\n        decayRoutingPenaltiesLocked(now);\n        routingQualityPenalty = RealtimeRoutingPolicy.penaltyAfterBadFile(\n                routingQualityPenalty);\n        routingLatencyPenalty = RealtimeRoutingPolicy.penaltyAfterBadFile(\n                routingLatencyPenalty);\n        goodRealtimeStreak = 0;\n        lastRoutingSignalAtMs = now;\n        outcomeBlockedUntilMs = RealtimeRoutingPolicy.shortenOutcomeBlockAfterBadFile(\n                now, outcomeBlockedUntilMs);\n    }\n'''
text = replace_once(text, old, new, 'client file performance health')
client.write_text(text)

service = ROOT / 'app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java'
text = service.read_text()
text = replace_once(text,
'''        boolean usableFileRoute = RealtimeRoutingPolicy.isUsableFileOutcome(\n                hasText(turnFocus), submittedChunks, failedChunks, lastChunkFailed);\n        if (realtimeTranscriber != null) {\n            realtimeTranscriber.notePrimaryFileTurnOutcome(\n                    usableFileRoute, lastFileDrainLatencyMs);\n        }\n''',
'''        boolean usableFileRoute = RealtimeRoutingPolicy.isUsableFileOutcome(\n                hasText(turnFocus), submittedChunks, failedChunks, lastChunkFailed);\n        boolean filePerformanceEligible = RealtimeRoutingPolicy.isFilePerformanceEligible(\n                usableFileRoute, degradedTurnStreak, retryCooldownRemainingMs);\n        if (realtimeTranscriber != null) {\n            realtimeTranscriber.notePrimaryFileTurnOutcome(\n                    usableFileRoute, filePerformanceEligible, lastFileDrainLatencyMs);\n        }\n''', 'service file performance health')
service.write_text(text)

test = ROOT / 'app/src/test/java/com/livecopilot/micprobe/RealtimeRoutingPolicyTest.java'
text = test.read_text()
needle = '''\n    @Test\n    public void connectedSocketIsRoutableOnlyAfterBlockExpires() {\n'''
insert = '''\n    @Test\n    public void filePerformanceEvidenceRequiresHealthyFileLane() {\n        assertTrue(RealtimeRoutingPolicy.isFilePerformanceEligible(true, 0, 0L));\n        assertFalse(RealtimeRoutingPolicy.isFilePerformanceEligible(true, 1, 0L));\n        assertFalse(RealtimeRoutingPolicy.isFilePerformanceEligible(true, 0, 1L));\n        assertFalse(RealtimeRoutingPolicy.isFilePerformanceEligible(false, 0, 0L));\n    }\n'''
text = replace_once(text, needle, insert + needle, 'file performance health test')
test.write_text(text)

print('v0.71 file performance health refinement applied')
