package com.livecopilot.micprobe;

final class RealtimeReconnectPolicy {
    static final long STABLE_RESET_MS = 15_000L;

    private RealtimeReconnectPolicy() {}

    static long delayMs(int attempt) {
        int safeAttempt = Math.max(0, Math.min(3, attempt));
        return 1_000L << safeAttempt;
    }
}
