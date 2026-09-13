package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PcmResamplerTest {

    @Test
    public void converts20ms16kFrameTo20ms24kFrame() {
        short[] input = sine(16_000, 440.0, 0.2, 320);
        short[] output = PcmResampler.resample(input, 16_000, 24_000);
        assertEquals(480, output.length);
        assertTrue(rms(output) > 0.10);
    }

    @Test
    public void sameRateReturnsEquivalentCopy() {
        short[] input = {1, -2, 3, -4};
        short[] output = PcmResampler.resample(input, 24_000, 24_000);
        assertEquals(input.length, output.length);
        for (int i = 0; i < input.length; i++) assertEquals(input[i], output[i]);
        assertTrue(input != output);
    }

    @Test
    public void invalidInputIsSafe() {
        assertEquals(0, PcmResampler.resample(new short[0], 16_000, 24_000).length);
        assertEquals(0, PcmResampler.resample(null, 16_000, 24_000).length);
        assertEquals(0, PcmResampler.resample(new short[]{1}, 0, 24_000).length);
    }

    private static short[] sine(int rate, double hz, double amplitude, int count) {
        short[] out = new short[count];
        for (int i = 0; i < count; i++) {
            out[i] = (short) Math.round(Math.sin(2.0 * Math.PI * hz * i / rate) * amplitude * 32767.0);
        }
        return out;
    }

    private static double rms(short[] samples) {
        double sum = 0.0;
        for (short s : samples) {
            double v = s / 32768.0;
            sum += v * v;
        }
        return Math.sqrt(sum / Math.max(1, samples.length));
    }
}
