package com.livecopilot.micprobe;

final class AdaptiveFileSttDeadlinePolicy {
    static final long MIN_DEADLINE_MS = 5_000L;
    static final long DEFAULT_DEADLINE_MS = 8_000L;
    static final long MAX_DEADLINE_MS = 10_000L;
    private static final long DRAIN_MARGIN_MS = 2_000L;
    private static final long TIMEOUT_RECOVERY_STEP_MS = 2_000L;

    private AdaptiveFileSttDeadlinePolicy() {}

    static long initialDeadlineMs() {
        return DEFAULT_DEADLINE_MS;
    }

    static long nextDeadlineMs(long currentDeadlineMs, long drainLatencyMs, boolean timedOut) {
        long current = clamp(currentDeadlineMs);
        if (timedOut) {
            return clamp(current + TIMEOUT_RECOVERY_STEP_MS);
        }

        long drain = Math.max(0L, drainLatencyMs);
        long target;
        long maxDrainBeforeCap = (MAX_DEADLINE_MS - DRAIN_MARGIN_MS) / 2L;
        if (drain >= maxDrainBeforeCap) {
            target = MAX_DEADLINE_MS;
        } else {
            target = clamp(drain * 2L + DRAIN_MARGIN_MS);
        }

        // Smooth successful samples so one unusually fast/slow turn cannot swing
        // the deadline aggressively. 25% of the new target is applied per turn.
        return clamp((current * 3L + target) / 4L);
    }

    private static long clamp(long value) {
        return Math.max(MIN_DEADLINE_MS, Math.min(MAX_DEADLINE_MS, value));
    }
}
