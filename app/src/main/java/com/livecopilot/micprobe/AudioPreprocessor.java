package com.livecopilot.micprobe;

final class AudioPreprocessor {
    private static final double HIGH_PASS_HZ = 80.0;
    private static final double TARGET_RMS = 0.075; // about -22.5 dBFS
    private static final double SILENCE_RMS = 0.0045;
    private static final double MIN_GAIN = 0.85;
    private static final double MAX_GAIN = 3.2;
    private static final double OUTPUT_PEAK_TARGET = 0.90;
    private static final double OUTPUT_LIMIT = 0.98;

    private AudioPreprocessor() {}

    static short[] prepare(short[] input, int sampleRate) {
        if (input == null || input.length == 0 || sampleRate <= 0) return new short[0];

        double[] filtered = highPass(input, sampleRate);
        short[] out = new short[input.length];

        // 20 ms frames. Gain moves down quickly when speech gets loud, but rises
        // more slowly so background noise between words is not suddenly amplified.
        int frameSamples = Math.max(80, sampleRate / 50);
        double gain = 1.0;

        for (int start = 0; start < filtered.length; start += frameSamples) {
            int end = Math.min(filtered.length, start + frameSamples);
            double sumSquares = 0.0;
            double framePeak = 0.0;

            for (int i = start; i < end; i++) {
                double v = filtered[i];
                sumSquares += v * v;
                framePeak = Math.max(framePeak, Math.abs(v));
            }

            int count = Math.max(1, end - start);
            double frameRms = Math.sqrt(sumSquares / count);
            double desiredGain;

            if (frameRms < SILENCE_RMS) {
                desiredGain = 1.0;
            } else {
                desiredGain = clamp(TARGET_RMS / frameRms, MIN_GAIN, MAX_GAIN);
                if (framePeak > 0.00001) {
                    desiredGain = Math.min(desiredGain, OUTPUT_PEAK_TARGET / framePeak);
                    desiredGain = Math.max(MIN_GAIN, desiredGain);
                }
            }

            double smoothing = desiredGain < gain ? 0.50 : 0.12;
            gain += (desiredGain - gain) * smoothing;

            for (int i = start; i < end; i++) {
                double v = clamp(filtered[i] * gain, -OUTPUT_LIMIT, OUTPUT_LIMIT);
                out[i] = (short) Math.round(v * 32767.0);
            }
        }

        return out;
    }

    private static double[] highPass(short[] input, int sampleRate) {
        double[] filtered = new double[input.length];
        double dt = 1.0 / sampleRate;
        double rc = 1.0 / (2.0 * Math.PI * HIGH_PASS_HZ);
        double alpha = rc / (rc + dt);

        double prevX = input[0] / 32768.0;
        double prevY = 0.0;

        for (int i = 0; i < input.length; i++) {
            double x = input[i] / 32768.0;
            double y = i == 0 ? 0.0 : alpha * (prevY + x - prevX);
            prevX = x;
            prevY = y;
            filtered[i] = y;
        }
        return filtered;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
