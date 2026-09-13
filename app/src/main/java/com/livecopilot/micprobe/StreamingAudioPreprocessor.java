package com.livecopilot.micprobe;

final class StreamingAudioPreprocessor {
    private static final double HIGH_PASS_HZ = 80.0;
    private static final double TARGET_RMS = 0.075;
    private static final double SILENCE_RMS = 0.0045;
    private static final double MIN_GAIN = 0.85;
    private static final double MAX_GAIN = 3.2;
    private static final double OUTPUT_PEAK_TARGET = 0.90;
    private static final double OUTPUT_LIMIT = 0.98;

    private int sampleRate;
    private double alpha;
    private double prevX;
    private double prevY;
    private double gain = 1.0;
    private boolean initialized;

    void reset(int sampleRate) {
        this.sampleRate = Math.max(1, sampleRate);
        double dt = 1.0 / this.sampleRate;
        double rc = 1.0 / (2.0 * Math.PI * HIGH_PASS_HZ);
        alpha = rc / (rc + dt);
        prevX = 0.0;
        prevY = 0.0;
        gain = 1.0;
        initialized = false;
    }

    short[] process(short[] input, int sampleRate) {
        if (input == null || input.length == 0 || sampleRate <= 0) return new short[0];
        if (this.sampleRate != sampleRate || alpha == 0.0) reset(sampleRate);

        double[] filtered = new double[input.length];
        double sumSquares = 0.0;
        double peak = 0.0;

        for (int i = 0; i < input.length; i++) {
            double x = input[i] / 32768.0;
            double y;
            if (!initialized) {
                y = 0.0;
                initialized = true;
            } else {
                y = alpha * (prevY + x - prevX);
            }
            prevX = x;
            prevY = y;
            filtered[i] = y;
            sumSquares += y * y;
            peak = Math.max(peak, Math.abs(y));
        }

        double rms = Math.sqrt(sumSquares / Math.max(1, filtered.length));
        double desiredGain;
        if (rms < SILENCE_RMS) {
            desiredGain = 1.0;
        } else {
            desiredGain = clamp(TARGET_RMS / rms, MIN_GAIN, MAX_GAIN);
            if (peak > 0.00001) {
                desiredGain = Math.min(desiredGain, OUTPUT_PEAK_TARGET / peak);
                desiredGain = Math.max(MIN_GAIN, desiredGain);
            }
        }

        double smoothing = desiredGain < gain ? 0.50 : 0.12;
        gain += (desiredGain - gain) * smoothing;

        short[] out = new short[input.length];
        for (int i = 0; i < filtered.length; i++) {
            double v = clamp(filtered[i] * gain, -OUTPUT_LIMIT, OUTPUT_LIMIT);
            out[i] = (short) Math.round(v * 32767.0);
        }
        return out;
    }

    double currentGain() {
        return gain;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
