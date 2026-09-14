from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)

p = Path("app/src/main/java/com/livecopilot/micprobe/MicProbeAccessibilityService.java")
s = p.read_text()

s = replace_once(
    s,
    "    private short[] fallbackTurnAudio;\n",
    "    private final PcmTurnBuffer fallbackTurnAudio = new PcmTurnBuffer(MAX_FALLBACK_AUDIO_SAMPLES);\n",
    "fallback field",
)

old = '''    private synchronized void appendFallbackTurnAudio(short[] samples, int sampleRate) {
        if (samples == null || samples.length == 0) return;
        fallbackTurnSampleRate = sampleRate;
        if (fallbackTurnAudio == null || fallbackTurnAudio.length == 0) {
            fallbackTurnAudio = samples.clone();
            return;
        }
        int total = Math.min(MAX_FALLBACK_AUDIO_SAMPLES, fallbackTurnAudio.length + samples.length);
        short[] next = new short[total];
        int keepOld = Math.min(fallbackTurnAudio.length, Math.max(0, total - samples.length));
        if (keepOld > 0) {
            System.arraycopy(fallbackTurnAudio, fallbackTurnAudio.length - keepOld, next, 0, keepOld);
        }
        int copyNew = Math.min(samples.length, total - keepOld);
        System.arraycopy(samples, samples.length - copyNew, next, keepOld, copyNew);
        fallbackTurnAudio = next;
    }

    private synchronized short[] takeFallbackPlus(short[] samples, int sampleRate) {
        if (fallbackTurnAudio == null || fallbackTurnAudio.length == 0 || fallbackTurnSampleRate != sampleRate) {
            clearFallbackTurnAudio();
            return samples == null ? new short[0] : samples;
        }
        appendFallbackTurnAudio(samples, sampleRate);
        short[] out = fallbackTurnAudio;
        fallbackTurnAudio = null;
        return out == null ? new short[0] : out;
    }

    private synchronized void submitBufferedFallback() {
        if (aiClient == null || fallbackTurnAudio == null || fallbackTurnAudio.length == 0) {
            clearFallbackTurnAudio();
            return;
        }
        short[] audio = fallbackTurnAudio;
        int rate = fallbackTurnSampleRate;
        fallbackTurnAudio = null;
        short[] prepared = AudioPreprocessor.prepare(audio, rate);
        if (prepared.length > 0) aiClient.submitAudio(prepared, rate);
    }

    private synchronized void clearFallbackTurnAudio() {
        fallbackTurnAudio = null;
        fallbackTurnSampleRate = 16_000;
    }
'''

new = '''    private synchronized void appendFallbackTurnAudio(short[] samples, int sampleRate) {
        if (samples == null || samples.length == 0) return;
        if (fallbackTurnAudio.size() > 0 && fallbackTurnSampleRate != sampleRate) {
            fallbackTurnAudio.clear();
        }
        fallbackTurnSampleRate = sampleRate;
        fallbackTurnAudio.append(samples);
    }

    private synchronized short[] takeFallbackPlus(short[] samples, int sampleRate) {
        if (fallbackTurnAudio.size() == 0 || fallbackTurnSampleRate != sampleRate) {
            clearFallbackTurnAudio();
            return samples == null ? new short[0] : samples;
        }
        appendFallbackTurnAudio(samples, sampleRate);
        short[] out = fallbackTurnAudio.copy();
        fallbackTurnAudio.clear();
        return out;
    }

    private synchronized void submitBufferedFallback() {
        if (aiClient == null || fallbackTurnAudio.size() == 0) {
            clearFallbackTurnAudio();
            return;
        }
        short[] audio = fallbackTurnAudio.copy();
        int rate = fallbackTurnSampleRate;
        fallbackTurnAudio.clear();
        short[] prepared = AudioPreprocessor.prepare(audio, rate);
        if (prepared.length > 0) aiClient.submitAudio(prepared, rate);
    }

    private synchronized void clearFallbackTurnAudio() {
        fallbackTurnAudio.clear();
        fallbackTurnSampleRate = 16_000;
    }
'''

s = replace_once(s, old, new, "fallback buffer methods")
p.write_text(s)
