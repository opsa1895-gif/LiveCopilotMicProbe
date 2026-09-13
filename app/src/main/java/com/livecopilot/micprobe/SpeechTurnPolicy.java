package com.livecopilot.micprobe;

final class SpeechTurnPolicy {
    private static final double SHORT_TURN_SECONDS = 2.4;
    private static final double MEDIUM_TURN_SECONDS = 4.2;

    private SpeechTurnPolicy() {}

    static int requiredSilenceFrames(int segmentSamples, int sampleRate, int frameSamples) {
        if (sampleRate <= 0 || frameSamples <= 0) return 30;
        double seconds = Math.max(0, segmentSamples) / (double) sampleRate;
        int silenceMs;
        if (seconds <= SHORT_TURN_SECONDS) {
            silenceMs = 460;
        } else if (seconds <= MEDIUM_TURN_SECONDS) {
            silenceMs = 520;
        } else {
            silenceMs = 600;
        }
        double frameMs = frameSamples * 1000.0 / sampleRate;
        return Math.max(1, (int) Math.ceil(silenceMs / frameMs));
    }

    static boolean shouldEndTurn(int consecutiveSilenceFrames,
                                 int segmentSamples,
                                 int sampleRate,
                                 int frameSamples,
                                 int minimumSegmentSamples) {
        if (segmentSamples < minimumSegmentSamples) return false;
        return consecutiveSilenceFrames >= requiredSilenceFrames(segmentSamples, sampleRate, frameSamples);
    }
}
