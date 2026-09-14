package com.livecopilot.micprobe;

final class VoiceActivityPolicy {
    private static final int STRONG_START_FRAMES = 2;
    private static final int SOFT_START_FRAMES = 6;

    private VoiceActivityPolicy() {}

    static double startThresholdDb(double noiseFloorDb) {
        return clamp(noiseFloorDb + 12.5, -49.0, -29.0);
    }

    static double softStartThresholdDb(double noiseFloorDb) {
        return clamp(noiseFloorDb + 6.0, -55.0, -34.0);
    }

    static double continueThresholdDb(double noiseFloorDb) {
        return clamp(noiseFloorDb + 4.5, -56.0, -34.0);
    }

    static int startFramesRequired(double frameDb, double noiseFloorDb, boolean silenced) {
        if (silenced) return 0;
        if (frameDb >= startThresholdDb(noiseFloorDb)) return STRONG_START_FRAMES;
        if (frameDb >= softStartThresholdDb(noiseFloorDb)) return SOFT_START_FRAMES;
        return 0;
    }

    static boolean isVoiced(double frameDb, double noiseFloorDb, boolean alreadySpeaking, boolean silenced) {
        if (silenced) return false;
        double threshold = alreadySpeaking
                ? continueThresholdDb(noiseFloorDb)
                : startThresholdDb(noiseFloorDb);
        return frameDb >= threshold;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
