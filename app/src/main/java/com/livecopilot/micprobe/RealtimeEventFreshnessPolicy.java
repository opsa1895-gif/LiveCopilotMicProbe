package com.livecopilot.micprobe;

final class RealtimeEventFreshnessPolicy {
    private RealtimeEventFreshnessPolicy() {}

    static boolean shouldAccept(long eventSpeechEpoch, long currentSpeechEpoch) {
        return eventSpeechEpoch > 0L && eventSpeechEpoch == currentSpeechEpoch;
    }
}
