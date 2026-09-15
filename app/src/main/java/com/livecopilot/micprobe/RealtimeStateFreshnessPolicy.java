package com.livecopilot.micprobe;

final class RealtimeStateFreshnessPolicy {
    private RealtimeStateFreshnessPolicy() {}

    static boolean shouldAccept(long callbackSerial, long currentSerial) {
        return callbackSerial > 0L && callbackSerial > currentSerial;
    }
}
