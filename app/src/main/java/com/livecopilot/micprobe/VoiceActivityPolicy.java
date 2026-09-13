package com.livecopilot.micprobe;

final class VoiceActivityPolicy {
    private VoiceActivityPolicy() {}

    static double startThresholdDb(double noiseFloorDb) {
        return clamp(noiseFloorDb + 12.5, -49.0, -29.0);
    }

    static double continueThresholdDb(double noiseFloorDb) {
        return clamp(noiseFloorDb + 7.0, -54.0, -33.0);
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
