package com.livecopilot.micprobe;

final class FileSttFreshnessPolicy {
    static final long MAX_SURFACE_AGE_MS = 10_000L;

    private FileSttFreshnessPolicy() {}

    static boolean shouldSurface(long itemSerial, long latestSerial, long ageMs, boolean sessionMatches) {
        return sessionMatches
                && itemSerial > 0L
                && itemSerial == latestSerial
                && ageMs >= 0L
                && ageMs <= MAX_SURFACE_AGE_MS;
    }
}
