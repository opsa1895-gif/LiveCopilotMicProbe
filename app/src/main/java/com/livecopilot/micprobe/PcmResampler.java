package com.livecopilot.micprobe;

final class PcmResampler {
    private PcmResampler() {}

    static short[] resample(short[] input, int inputRate, int outputRate) {
        if (input == null || input.length == 0 || inputRate <= 0 || outputRate <= 0) {
            return new short[0];
        }
        if (inputRate == outputRate) return input.clone();

        int outputLength = Math.max(1, (int) Math.round(input.length * (outputRate / (double) inputRate)));
        short[] out = new short[outputLength];
        double step = inputRate / (double) outputRate;

        for (int i = 0; i < outputLength; i++) {
            double source = i * step;
            int left = (int) Math.floor(source);
            if (left >= input.length - 1) {
                out[i] = input[input.length - 1];
                continue;
            }
            int right = left + 1;
            double fraction = source - left;
            double value = input[left] * (1.0 - fraction) + input[right] * fraction;
            out[i] = (short) Math.round(Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value)));
        }
        return out;
    }
}
