package com.livecopilot.micprobe;

final class AudioPreprocessor {
    private AudioPreprocessor() {}

    static short[] prepare(short[] input, int sampleRate) {
        if (input == null || input.length == 0 || sampleRate <= 0) return new short[0];

        double[] filtered = new double[input.length];
        double dt = 1.0 / sampleRate;
        double rc = 1.0 / (2.0 * Math.PI * 80.0);
        double alpha = rc / (rc + dt);

        double prevX = input[0] / 32768.0;
        double prevY = 0.0;
        double sumSquares = 0.0;
        double peak = 0.0;

        for (int i = 0; i < input.length; i++) {
            double x = input[i] / 32768.0;
            double y = i == 0 ? 0.0 : alpha * (prevY + x - prevX);
            prevX = x;
            prevY = y;
            filtered[i] = y;
            sumSquares += y * y;
            peak = Math.max(peak, Math.abs(y));
        }

        double rms = Math.sqrt(sumSquares / Math.max(1, filtered.length));
        double targetRms = 0.075; // about -22.5 dBFS
        double gain = rms > 0.00001 ? targetRms / rms : 1.0;
        gain = clamp(gain, 1.0, 3.5);
        if (peak > 0.00001) gain = Math.min(gain, 0.92 / peak);
        gain = Math.max(0.65, gain);

        short[] out = new short[input.length];
        for (int i = 0; i < filtered.length; i++) {
            double v = filtered[i] * gain;
            v = clamp(v, -0.98, 0.98);
            out[i] = (short) Math.round(v * 32767.0);
        }
        return out;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
