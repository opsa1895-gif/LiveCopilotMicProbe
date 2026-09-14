from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:180]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


policy = "app/src/main/java/com/livecopilot/micprobe/VoiceActivityPolicy.java"
engine = "app/src/main/java/com/livecopilot/micprobe/MicProbeEngine.java"
test = "app/src/test/java/com/livecopilot/micprobe/VoiceActivityPolicyTest.java"
gradle = "app/build.gradle.kts"

Path(policy).write_text('''package com.livecopilot.micprobe;\n\nfinal class VoiceActivityPolicy {\n    private static final int STRONG_START_FRAMES = 2;\n    private static final int SOFT_START_FRAMES = 6;\n\n    private VoiceActivityPolicy() {}\n\n    static double startThresholdDb(double noiseFloorDb) {\n        return clamp(noiseFloorDb + 12.5, -49.0, -29.0);\n    }\n\n    static double softStartThresholdDb(double noiseFloorDb) {\n        return clamp(noiseFloorDb + 6.0, -55.0, -34.0);\n    }\n\n    static double continueThresholdDb(double noiseFloorDb) {\n        return clamp(noiseFloorDb + 4.5, -56.0, -34.0);\n    }\n\n    static int startFramesRequired(double frameDb, double noiseFloorDb, boolean silenced) {\n        if (silenced) return 0;\n        if (frameDb >= startThresholdDb(noiseFloorDb)) return STRONG_START_FRAMES;\n        if (frameDb >= softStartThresholdDb(noiseFloorDb)) return SOFT_START_FRAMES;\n        return 0;\n    }\n\n    static boolean isVoiced(double frameDb, double noiseFloorDb, boolean alreadySpeaking, boolean silenced) {\n        if (silenced) return false;\n        double threshold = alreadySpeaking\n                ? continueThresholdDb(noiseFloorDb)\n                : startThresholdDb(noiseFloorDb);\n        return frameDb >= threshold;\n    }\n\n    private static double clamp(double value, double min, double max) {\n        return Math.max(min, Math.min(max, value));\n    }\n}\n''', encoding="utf-8")

Path(test).write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n\npublic class VoiceActivityPolicyTest {\n\n    @Test\n    public void quietFrameCanContinueExistingSpeechWithoutStrongStart() {\n        double noiseFloor = -58.0;\n        double frame = -49.0;\n\n        assertFalse(VoiceActivityPolicy.isVoiced(frame, noiseFloor, false, false));\n        assertTrue(VoiceActivityPolicy.isVoiced(frame, noiseFloor, true, false));\n    }\n\n    @Test\n    public void clearSpeechStartsWithTwoFrames() {\n        assertEquals(2, VoiceActivityPolicy.startFramesRequired(-42.0, -58.0, false));\n        assertTrue(VoiceActivityPolicy.isVoiced(-42.0, -58.0, false, false));\n    }\n\n    @Test\n    public void sustainedQuietSpeechGetsSoftSixFrameStart() {\n        assertEquals(6, VoiceActivityPolicy.startFramesRequired(-52.0, -58.0, false));\n        assertTrue(VoiceActivityPolicy.isVoiced(-52.0, -58.0, true, false));\n    }\n\n    @Test\n    public void backgroundNoiseDoesNotOpenTurn() {\n        assertEquals(0, VoiceActivityPolicy.startFramesRequired(-54.0, -58.0, false));\n        assertFalse(VoiceActivityPolicy.isVoiced(-54.0, -58.0, false, false));\n    }\n\n    @Test\n    public void silencedClientNeverCountsAsVoice() {\n        assertEquals(0, VoiceActivityPolicy.startFramesRequired(-10.0, -60.0, true));\n        assertFalse(VoiceActivityPolicy.isVoiced(-10.0, -60.0, true, true));\n    }\n\n    @Test\n    public void thresholdsFollowHigherNoiseFloorButStayBounded() {\n        double strong = VoiceActivityPolicy.startThresholdDb(-42.0);\n        double soft = VoiceActivityPolicy.softStartThresholdDb(-42.0);\n        double keep = VoiceActivityPolicy.continueThresholdDb(-42.0);\n\n        assertTrue(strong > soft);\n        assertTrue(soft > keep);\n        assertTrue(strong <= -29.0);\n        assertTrue(soft <= -34.0);\n        assertTrue(keep <= -34.0);\n    }\n}\n''', encoding="utf-8")

replace_once(
    engine,
    '''    private static final int START_VOICE_FRAMES = 2; // 40 ms\n''',
    '',
)

replace_once(
    engine,
    '''                boolean voiced = VoiceActivityPolicy.isVoiced(\n                        latestDb, noiseFloorDb, speaking, silenced);\n''',
    '''                int startFramesRequired = speaking\n                        ? 0\n                        : VoiceActivityPolicy.startFramesRequired(latestDb, noiseFloorDb, silenced);\n                boolean voiced = speaking\n                        ? VoiceActivityPolicy.isVoiced(latestDb, noiseFloorDb, true, silenced)\n                        : startFramesRequired > 0;\n''',
)

replace_once(
    engine,
    '''                    if (consecutiveVoiceFrames >= START_VOICE_FRAMES) {\n''',
    '''                    if (startFramesRequired > 0 && consecutiveVoiceFrames >= startFramesRequired) {\n''',
)

replace_once(
    gradle,
    '''        versionCode = 37\n        versionName = "0.36.0-fast-actionable"\n''',
    '''        versionCode = 38\n        versionName = "0.37.0-quiet-speech-vad"\n''',
)
