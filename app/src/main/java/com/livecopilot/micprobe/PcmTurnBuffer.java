package com.livecopilot.micprobe;

final class PcmTurnBuffer {
    private final int maxSamples;
    private short[] data;
    private int size;

    PcmTurnBuffer(int maxSamples) {
        this.maxSamples = Math.max(1, maxSamples);
        data = new short[Math.min(this.maxSamples, 16_384)];
    }

    void clear() {
        size = 0;
    }

    void append(short[] input) {
        if (input == null || input.length == 0) return;

        if (input.length >= maxSamples) {
            ensure(maxSamples);
            System.arraycopy(input, input.length - maxSamples, data, 0, maxSamples);
            size = maxSamples;
            return;
        }

        int overflow = Math.max(0, size + input.length - maxSamples);
        if (overflow > 0) {
            int keep = size - overflow;
            if (keep > 0) System.arraycopy(data, overflow, data, 0, keep);
            size = Math.max(0, keep);
        }

        ensure(size + input.length);
        System.arraycopy(input, 0, data, size, input.length);
        size += input.length;
    }

    int size() {
        return size;
    }

    short[] copy() {
        short[] out = new short[size];
        System.arraycopy(data, 0, out, 0, size);
        return out;
    }

    private void ensure(int needed) {
        if (needed <= data.length) return;
        int nextSize = Math.min(maxSamples, Math.max(needed, data.length * 2));
        short[] next = new short[nextSize];
        System.arraycopy(data, 0, next, 0, size);
        data = next;
    }
}
