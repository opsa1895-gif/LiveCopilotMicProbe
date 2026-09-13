package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AudioPreprocessorTest {

    @Test
    public void quietSpeechGetsAmplifiedWithoutClipping() {
        int sampleRate = 16_000;
        short[] input = sine(sampleRate, 440.0, 0.018, 1.5);
        double inRms = rms(input);
        short[] output = AudioPreprocessor.prepare(input, sampleRate);
        double outRms = rms(output);

        assertTrue("quiet speech should be amplified", outRms > inRms * 1.8);
        assertTrue("normalization must stay below clipping", peak(output) < 0.99);
    }

    @Test
    public void alreadyStrongSpeechStaysConservative() {
        int sampleRate = 16_000;
        short[] input = sine(sampleRate, 650.0, 0.28, 1.0);
        short[] output = AudioPreprocessor.prepare(input, sampleRate);

        assertTrue("strong speech should not clip", peak(output) < 0.99);
        assertTrue("strong speech should not be boosted dramatically", rms(output) < rms(input) * 1.25);
        assertTrue("strong speech should not be crushed", rms(output) > rms(input) * 0.65);
    }

    @Test
    public void quietPartStillGetsHelpWhenSameChunkContainsLoudSpeaker() {
        int sampleRate = 16_000;
        short[] quiet = sine(sampleRate, 440.0, 0.018, 1.0);
        short[] loud = sine(sampleRate, 440.0, 0.22, 1.0);
        short[] input = concat(quiet, loud);

        short[] output = AudioPreprocessor.prepare(input, sampleRate);
        short[] quietOut = slice(output, 0, sampleRate);
        short[] loudOut = slice(output, sampleRate, output.length);

        assertTrue("quiet speaker should be boosted inside a mixed chunk", rms(quietOut) > rms(quiet) * 1.7);
        assertTrue("loud speaker should remain usable", rms(loudOut) > rms(loud) * 0.65);
        assertTrue("mixed chunk must not clip", peak(output) < 0.99);
    }

    @Test
    public void dcAndVeryLowFrequencyEnergyIsReduced() {
        int sampleRate = 16_000;
        short[] input = new short[sampleRate * 2];
        for (int i = 0; i < input.length; i++) {
            double low = Math.sin(2.0 * Math.PI * 20.0 * i / sampleRate) * 0.18;
            double dc = 0.12;
            input[i] = toPcm(low + dc);
        }

        short[] output = AudioPreprocessor.prepare(input, sampleRate);
        assertTrue("high-pass should strongly reduce DC/20Hz rumble", rms(output) < rms(input) * 0.55);
    }

    @Test
    public void emptyInputIsSafe() {
        short[] output = AudioPreprocessor.prepare(new short[0], 16_000);
        assertTrue(output.length == 0);
    }

    private static short[] sine(int sampleRate, double hz, double amplitude, double seconds) {
        int count = (int) Math.round(sampleRate * seconds);
        short[] out = new short[count];
        for (int i = 0; i < count; i++) {
            out[i] = toPcm(Math.sin(2.0 * Math.PI * hz * i / sampleRate) * amplitude);
        }
        return out;
    }

    private static short[] concat(short[] a, short[] b) {
        short[] out = new short[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static short[] slice(short[] input, int start, int end) {
        int safeStart = Math.max(0, Math.min(start, input.length));
        int safeEnd = Math.max(safeStart, Math.min(end, input.length));
        short[] out = new short[safeEnd - safeStart];
        System.arraycopy(input, safeStart, out, 0, out.length);
        return out;
    }

    private static short toPcm(double value) {
        double v = Math.max(-0.999, Math.min(0.999, value));
        return (short) Math.round(v * 32767.0);
    }

    private static double rms(short[] samples) {
        if (samples.length == 0) return 0.0;
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
