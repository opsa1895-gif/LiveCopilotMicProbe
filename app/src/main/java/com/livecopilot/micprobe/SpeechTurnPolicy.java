package com.livecopilot.micprobe;

final class SpeechTurnPolicy {
    private static final double VERY_SHORT_VOICE_SECONDS = 1.0;
    private static final double SHORT_VOICE_SECONDS = 2.2;
    private static final double MEDIUM_VOICE_SECONDS = 4.0;

    private SpeechTurnPolicy() {}

    static int requiredSilenceFrames(int voicedFrames, int sampleRate, int frameSamples) {
        if (sampleRate <= 0 || frameSamples <= 0) return 29;
        double voicedSeconds = Math.max(0, voicedFrames) * frameSamples / (double) sampleRate;
        int silenceMs;
        if (voicedSeconds <= VERY_SHORT_VOICE_SECONDS) {
            silenceMs = 360;
        } else if (voicedSeconds <= SHORT_VOICE_SECONDS) {
            silenceMs = 420;
        } else if (voicedSeconds <= MEDIUM_VOICE_SECONDS) {
            silenceMs = 500;
        } else {
            silenceMs = 580;
        }
        double frameMs = frameSamples * 1000.0 / sampleRate;
        return Math.max(1, (int) Math.ceil(silenceMs / frameMs));
    }

    static boolean shouldEndTurn(int consecutiveSilenceFrames,
                                 int segmentSamples,
                                 int voicedFrames,
                                 int sampleRate,
                                 int frameSamples,
                                 int minimumSegmentSamples) {
        if (segmentSamples < minimumSegmentSamples || voicedFrames < 4) return false;
        return consecutiveSilenceFrames >= requiredSilenceFrames(voicedFrames, sampleRate, frameSamples);
    }
}
