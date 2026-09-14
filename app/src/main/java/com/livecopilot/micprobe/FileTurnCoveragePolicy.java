package com.livecopilot.micprobe;

final class FileTurnCoveragePolicy {
    static final int MIN_CONSERVATIVE_COVERAGE_PERCENT = 67;

    private FileTurnCoveragePolicy() {}

    static int coveragePercent(int submittedChunks, int failedChunks) {
        if (submittedChunks <= 0) return 0;
        int failed = Math.max(0, Math.min(submittedChunks, failedChunks));
        int usable = submittedChunks - failed;
        return (usable * 100) / submittedChunks;
    }

    static boolean allowMainReply(String turnFocus, int submittedChunks,
                                  int failedChunks, boolean lastChunkFailed) {
        return hasFocus(turnFocus)
                && submittedChunks > 0
                && failedChunks <= 0
                && !lastChunkFailed;
    }

    static boolean allowSemanticFallback(boolean mainReplyQueued, String turnFocus,
                                         int submittedChunks, int failedChunks,
                                         boolean lastChunkFailed) {
        if (mainReplyQueued || !hasFocus(turnFocus) || submittedChunks <= 0) return false;
        if (failedChunks <= 0) return true;
        if (lastChunkFailed) return false;
        return coveragePercent(submittedChunks, failedChunks)
                >= MIN_CONSERVATIVE_COVERAGE_PERCENT;
    }

    static boolean isConservativeSemantic(int submittedChunks, int failedChunks,
                                          boolean lastChunkFailed) {
        return submittedChunks > 0 && failedChunks > 0 && !lastChunkFailed
                && coveragePercent(submittedChunks, failedChunks)
                >= MIN_CONSERVATIVE_COVERAGE_PERCENT;
    }

    private static boolean hasFocus(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
