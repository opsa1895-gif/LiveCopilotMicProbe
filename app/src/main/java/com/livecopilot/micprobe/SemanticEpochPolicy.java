package com.livecopilot.micprobe;

final class SemanticEpochPolicy {
    private SemanticEpochPolicy() {}

    static boolean shouldRun(long scheduledEpoch, long currentEpoch) {
        return scheduledEpoch > 0L && scheduledEpoch == currentEpoch;
    }
}
