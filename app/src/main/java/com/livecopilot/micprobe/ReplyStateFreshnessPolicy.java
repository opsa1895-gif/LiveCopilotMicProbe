package com.livecopilot.micprobe;

final class ReplyStateFreshnessPolicy {
    private ReplyStateFreshnessPolicy() {}

    static boolean shouldApply(long jobSessionSerial,
                               long currentSessionSerial,
                               long jobSerial,
                               long currentSerial,
                               long ageMs,
                               long maxAgeMs,
                               boolean closed) {
        return AsyncCallbackFreshnessPolicy.shouldAccept(
                jobSessionSerial, currentSessionSerial, jobSerial, currentSerial, closed)
                && ageMs >= 0L
                && ageMs <= maxAgeMs;
    }
}
