from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected 1 match, got {count} for {old[:180]!r}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")


policy = "app/src/main/java/com/livecopilot/micprobe/SpeechTurnPolicy.java"
engine = "app/src/main/java/com/livecopilot/micprobe/MicProbeEngine.java"
test = "app/src/test/java/com/livecopilot/micprobe/SpeechTurnPolicyTest.java"
gradle = "app/build.gradle.kts"

Path(policy).write_text('''package com.livecopilot.micprobe;\n\nfinal class SpeechTurnPolicy {\n    private static final double VERY_SHORT_VOICE_SECONDS = 1.0;\n    private static final double SHORT_VOICE_SECONDS = 2.2;\n    private static final double MEDIUM_VOICE_SECONDS = 4.0;\n\n    private SpeechTurnPolicy() {}\n\n    static int requiredSilenceFrames(int voicedFrames, int sampleRate, int frameSamples) {\n        if (sampleRate <= 0 || frameSamples <= 0) return 29;\n        double voicedSeconds = Math.max(0, voicedFrames) * frameSamples / (double) sampleRate;\n        int silenceMs;\n        if (voicedSeconds <= VERY_SHORT_VOICE_SECONDS) {\n            silenceMs = 360;\n        } else if (voicedSeconds <= SHORT_VOICE_SECONDS) {\n            silenceMs = 420;\n        } else if (voicedSeconds <= MEDIUM_VOICE_SECONDS) {\n            silenceMs = 500;\n        } else {\n            silenceMs = 580;\n        }\n        double frameMs = frameSamples * 1000.0 / sampleRate;\n        return Math.max(1, (int) Math.ceil(silenceMs / frameMs));\n    }\n\n    static boolean shouldEndTurn(int consecutiveSilenceFrames,\n                                 int segmentSamples,\n                                 int voicedFrames,\n                                 int sampleRate,\n                                 int frameSamples,\n                                 int minimumSegmentSamples) {\n        if (segmentSamples < minimumSegmentSamples || voicedFrames < 4) return false;\n        return consecutiveSilenceFrames >= requiredSilenceFrames(voicedFrames, sampleRate, frameSamples);\n    }\n}\n''', encoding="utf-8")

Path(test).write_text('''package com.livecopilot.micprobe;\n\nimport org.junit.Test;\n\nimport static org.junit.Assert.assertEquals;\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n\npublic class SpeechTurnPolicyTest {\n    private static final int SAMPLE_RATE = 16_000;\n    private static final int FRAME_SAMPLES = 320;\n    private static final int MIN_SEGMENT = SAMPLE_RATE * 3 / 4;\n\n    @Test\n    public void veryShortSpeechUsesFast360msEndpoint() {\n        assertEquals(18, SpeechTurnPolicy.requiredSilenceFrames(20, SAMPLE_RATE, FRAME_SAMPLES));\n    }\n\n    @Test\n    public void shortSpeechUses420msEndpoint() {\n        assertEquals(21, SpeechTurnPolicy.requiredSilenceFrames(75, SAMPLE_RATE, FRAME_SAMPLES));\n    }\n\n    @Test\n    public void mediumSpeechKeeps500msPause() {\n        assertEquals(25, SpeechTurnPolicy.requiredSilenceFrames(150, SAMPLE_RATE, FRAME_SAMPLES));\n    }\n\n    @Test\n    public void longSpeechKeeps580msPause() {\n        assertEquals(29, SpeechTurnPolicy.requiredSilenceFrames(250, SAMPLE_RATE, FRAME_SAMPLES));\n    }\n\n    @Test\n    public void shortQuestionCanEndBeforeOneSecondTotalSegment() {\n        assertFalse(SpeechTurnPolicy.shouldEndTurn(17, 13_000, 20, SAMPLE_RATE, FRAME_SAMPLES, MIN_SEGMENT));\n        assertTrue(SpeechTurnPolicy.shouldEndTurn(18, 13_000, 20, SAMPLE_RATE, FRAME_SAMPLES, MIN_SEGMENT));\n    }\n\n    @Test\n    public void neverEndsBeforeMinimumSegmentLength() {\n        assertFalse(SpeechTurnPolicy.shouldEndTurn(100, SAMPLE_RATE / 2, 30, SAMPLE_RATE, FRAME_SAMPLES, MIN_SEGMENT));\n    }\n\n    @Test\n    public void noiseBurstWithoutEnoughVoicedFramesDoesNotEndTurn() {\n        assertFalse(SpeechTurnPolicy.shouldEndTurn(100, SAMPLE_RATE, 3, SAMPLE_RATE, FRAME_SAMPLES, MIN_SEGMENT));\n    }\n}\n''', encoding="utf-8")

replace_once(
    engine,
    '''    private static final int MIN_SEGMENT_SAMPLES = SAMPLE_RATE;\n''',
    '''    private static final int MIN_SEGMENT_SAMPLES = SAMPLE_RATE * 3 / 4;\n''',
)

replace_once(
    engine,
    '''                    } else if (SpeechTurnPolicy.shouldEndTurn(\n                            consecutiveSilenceFrames,\n                            segment.size(),\n                            SAMPLE_RATE,\n                            FRAME_SAMPLES,\n                            MIN_SEGMENT_SAMPLES)) {\n''',
    '''                    } else if (SpeechTurnPolicy.shouldEndTurn(\n                            consecutiveSilenceFrames,\n                            segment.size(),\n                            voicedFramesInSegment,\n                            SAMPLE_RATE,\n                            FRAME_SAMPLES,\n                            MIN_SEGMENT_SAMPLES)) {\n''',
)

replace_once(
    gradle,
    '''        versionCode = 35\n        versionName = "0.34.0-recent-context"\n''',
    '''        versionCode = 36\n        versionName = "0.35.0-adaptive-endpointing"\n''',
)
