package com.livecopilot.micprobe;

import java.io.IOException;

final class FileSttRecoveryPolicy {
    enum DropReason {
        TIMEOUT,
        NETWORK,
        QUALITY,
        OTHER
    }

    private static final long SECOND_DEGRADED_COOLDOWN_MS = 8_000L;
    private static final long THIRD_DEGRADED_COOLDOWN_MS = 20_000L;
    private static final long MAX_COOLDOWN_MS = 30_000L;

    private FileSttRecoveryPolicy() {}

    static boolean allowRetry(long nowMs, long cooldownUntilMs) {
        return cooldownUntilMs <= 0L || nowMs >= cooldownUntilMs;
    }

    static boolean shouldRetryFailure(Throwable error, int completedAttempts,
                                      boolean retryAllowed) {
        return retryAllowed && completedAttempts < 2 && isNetworkLike(error);
    }

    static boolean shouldDegradeTurn(int submittedChunks, int usableChunks,
                                     int timeoutDrops, int networkDrops) {
        return submittedChunks > 0
                && usableChunks <= 0
                && Math.max(0, timeoutDrops) + Math.max(0, networkDrops) > 0;
    }

    static long cooldownMsForStreak(int degradedTurnStreak) {
        if (degradedTurnStreak < 2) return 0L;
        if (degradedTurnStreak == 2) return SECOND_DEGRADED_COOLDOWN_MS;
        if (degradedTurnStreak == 3) return THIRD_DEGRADED_COOLDOWN_MS;
        return MAX_COOLDOWN_MS;
    }

    static boolean isNetworkLike(Throwable error) {
        Throwable cursor = error;
        for (int depth = 0; cursor != null && depth < 6; depth++, cursor = cursor.getCause()) {
            if (cursor instanceof IOException) return true;
            String message = cursor.getMessage();
            if (message == null || !message.startsWith("STT ")) continue;
            try {
                int code = Integer.parseInt(message.substring(4).trim());
                if (code == 408 || code == 429 || code >= 500) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }
}
