package com.livecopilot.micprobe;

final class SemanticSchedulingPolicy {
    private static final long FAST_ACTIONABLE_MIN_GAP_MS = 900L;
    private static final long DEFAULT_MIN_GAP_MS = 2_500L;

    private SemanticSchedulingPolicy() {}

    static long minGapMs(String focus) {
        return OpenAiCopilotClient.isActionable(focus == null ? "" : focus)
                ? FAST_ACTIONABLE_MIN_GAP_MS
                : DEFAULT_MIN_GAP_MS;
    }
}
