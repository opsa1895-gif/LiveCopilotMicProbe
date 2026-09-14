package com.livecopilot.micprobe;

final class RealtimeCommitPolicy {
    static final long SOFT_TIMEOUT_MS = 4_500L;
    static final long PARTIAL_GRACE_MS = 2_500L;

    private RealtimeCommitPolicy() {}

    static boolean grantPartialGrace(boolean finalDeadline, boolean hasPartial) {
        return !finalDeadline && hasPartial;
    }
}
