from pathlib import Path

p = Path('app/src/main/java/com/livecopilot/micprobe/RealtimeTranscriptionClient.java')
text = p.read_text()

def rep(old, new, label):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    text = text.replace(old, new, 1)

rep(
'''    synchronized void notePrimaryFileTurnOutcome(boolean usable) {\n        if (closed || !wanted || usable) return;\n        long now = System.currentTimeMillis();\n''',
'''    synchronized void notePrimaryFileTurnOutcome(boolean usable) {\n        if (closed || !wanted) return;\n        goodRealtimeStreak = 0;\n        if (usable) return;\n        long now = System.currentTimeMillis();\n''',
'file turn breaks good streak')

rep(
'''    private void decayRoutingPenaltiesLocked(long now) {\n        if (lastRoutingSignalAtMs <= 0L || now <= lastRoutingSignalAtMs) return;\n        long idleMs = now - lastRoutingSignalAtMs;\n        if (idleMs < RealtimeRoutingPolicy.PENALTY_DECAY_STEP_MS) return;\n\n        routingQualityPenalty = RealtimeRoutingPolicy.decayedPenalty(\n''',
'''    private void decayRoutingPenaltiesLocked(long now) {\n        if (lastRoutingSignalAtMs <= 0L || now <= lastRoutingSignalAtMs) return;\n        long idleMs = now - lastRoutingSignalAtMs;\n        if (idleMs < RealtimeRoutingPolicy.PENALTY_DECAY_STEP_MS) return;\n\n        // A long idle gap breaks the meaning of "consecutive" healthy turns.\n        goodRealtimeStreak = 0;\n        routingQualityPenalty = RealtimeRoutingPolicy.decayedPenalty(\n''',
'idle breaks good streak')

rep(
'''    private void noteReadyFailureLocked() {\n        long now = System.currentTimeMillis();\n        if (RealtimeRoutingPolicy.isQuickFailure(\n''',
'''    private void noteReadyFailureLocked() {\n        long now = System.currentTimeMillis();\n        goodRealtimeStreak = 0;\n        if (RealtimeRoutingPolicy.isQuickFailure(\n''',
'transport failure breaks good streak')

p.write_text(text)
print('v0.70 consecutive-good refinement applied')
