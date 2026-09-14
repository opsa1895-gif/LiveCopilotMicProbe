package com.livecopilot.micprobe;

final class AsyncCallbackFreshnessPolicy {
    private AsyncCallbackFreshnessPolicy() {}

    static boolean shouldAccept(long callbackSessionSerial,
                                long currentSessionSerial,
                                long callbackSerial,
                                long currentSerial,
                                boolean closed) {
        return !closed
                && callbackSessionSerial > 0L
                && callbackSessionSerial == currentSessionSerial
                && callbackSerial > 0L
                && callbackSerial == currentSerial;
    }
}
