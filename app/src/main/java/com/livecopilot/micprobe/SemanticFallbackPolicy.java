package com.livecopilot.micprobe;

import java.io.IOException;

final class SemanticFallbackPolicy {
    static final int MAX_DECISION_ATTEMPTS = 2;
    static final long RETRY_DELAY_MS = 250L;

    private SemanticFallbackPolicy() {}

    static boolean shouldGenerateVariants(boolean partialInput) {
        return !partialInput;
    }

    static boolean shouldRetryHttp(int statusCode, int completedAttempts) {
        if (completedAttempts >= MAX_DECISION_ATTEMPTS) return false;
        return statusCode == 408
                || statusCode == 429
                || (statusCode >= 500 && statusCode <= 599);
    }

    static boolean shouldRetryFailure(Throwable error, int completedAttempts) {
        return completedAttempts < MAX_DECISION_ATTEMPTS && error instanceof IOException;
    }

    static long retryDelayMs(int nextAttempt) {
        return nextAttempt <= 1 ? 0L : RETRY_DELAY_MS;
    }
}
