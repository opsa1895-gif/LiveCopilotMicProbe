package com.livecopilot.micprobe;

final class RealtimeRecoveryPolicy {
    static final long MAX_DELIVERY_AGE_MS = 8_000L;

    private RealtimeRecoveryPolicy() {}

    static boolean isSuperseded(long committedSpeechEpoch, long currentSpeechEpoch) {
        return committedSpeechEpoch != currentSpeechEpoch;
    }

    static boolean shouldDeliver(long recoveryId, long latestRecoveryId, long ageMs, boolean sessionMatches) {
        return sessionMatches
                && recoveryId > 0L
                && recoveryId == latestRecoveryId
                && ageMs >= 0L
                && ageMs <= MAX_DELIVERY_AGE_MS;
    }
}
