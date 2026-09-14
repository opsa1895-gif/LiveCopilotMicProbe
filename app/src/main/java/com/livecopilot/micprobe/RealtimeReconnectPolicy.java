package com.livecopilot.micprobe;

final class RealtimeReconnectPolicy {
    static final long STABLE_RESET_MS = 15_000L;
    static final long MAX_RETRY_DELAY_MS = 30_000L;

    private RealtimeReconnectPolicy() {}

    static long delayMs(int attempt) {
        int safeAttempt = Math.max(0, Math.min(5, attempt));
        long exponentialDelayMs = 1_000L << safeAttempt;
        return Math.min(MAX_RETRY_DELAY_MS, exponentialDelayMs);
    }
}
