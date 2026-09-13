package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class StreamingAudioPreprocessorTest {

    @Test
    public void quietSpeechGainBuildsAcrossFrames() {
        StreamingAudioPreprocessor p = new StreamingAudioPreprocessor();
        int rate = 16_000;
        short[] frame = sine(rate, 440.0, 0.018, 320, 0);
        double inputRms = rms(frame);
        short[] output = null;

        for (int n = 0; n < 12; n++) {
            output = p.process(sine(rate, 440.0, 0.018, 320, n * 320), rate);
        }

        assertTrue(output != null && rms(output) > inputRms * 1.7);
        assertTrue(p.currentGain() > 1.7);
        assertTrue(peak(output) < 0.99);
    }

    @Test
    public void loudFramePullsGainDownQuicklyWithoutClipping() {
        StreamingAudioPreprocessor p = new StreamingAudioPreprocessor();
        int rate = 16_000;
        for (int n = 0; n < 12; n++) {
            p.process(sine(rate, 440.0, 0.018, 320, n * 320), rate);
        }
        double highGain = p.currentGain();
        short[] loud = p.process(sine(rate, 440.0, 0.35, 320, 12 * 320), rate);

        assertTrue(p.currentGain() < highGain);
        assertTrue(peak(loud) < 0.99);
    }

    @Test
    public void filterStateSuppressesDcAcrossConsecutiveFrames() {
        StreamingAudioPreprocessor p = new StreamingAudioPreprocessor();
        int rate = 16_000;
        short[] dc = new short[320];
        for (int i = 0; i < dc.length; i++) dc[i] = (short) Math.round(0.15 * 32767.0);

        short[] output = null;
        for (int n = 0; n < 10; n++) output = p.process(dc, rate);
        assertTrue(output != null && rms(output) < 0.015);
    }

    @Test
    public void sampleRateChangeResetsSafely() {
        StreamingAudioPreprocessor p = new StreamingAudioPreprocessor();
        short[] a = p.process(sine(16_000, 440, 0.05, 320, 0), 16_000);
        short[] b = p.process(sine(24_000, 440, 0.05, 480, 0), 24_000);
        assertTrue(a.length == 320);
        assertTrue(b.length == 480);
        assertTrue(peak(b) < 0.99);
    }

    private static short[] sine(int rate, double hz, double amplitude, int count, int offset) {
        short[] out = new short[count];
        for (int i = 0; i < count; i++) {
            double phase = 2.0 * Math.PI * hz * (i + offset) / rate;
            out[i] = (short) Math.round(Math.sin(phase) * amplitude * 32767.0);
        }
        return out;
    }

    private static double rms(short[] samples) {
        if (samples == null || samples.length == 0) return 0.0;
        double sum = 0.0;
        for (short s : samples) {
            double v = s / 32768.0;
            sum += v * v;
        }
        return Math.sqrt(sum / samples.length);
    }

    private static double peak(short[] samples) {
        double p = 0.0;
        for (short s : samples) p = Math.max(p, Math.abs(s / 32768.0));
        return p;
    }
}
