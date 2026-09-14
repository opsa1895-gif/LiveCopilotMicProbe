package com.livecopilot.micprobe;

final class PcmTurnBuffer {
    private final int maxSamples;
    private short[] data;
    private int start;
    private int size;

    PcmTurnBuffer(int maxSamples) {
        this.maxSamples = Math.max(1, maxSamples);
        data = new short[Math.min(this.maxSamples, 16_384)];
    }

    void clear() {
        start = 0;
        size = 0;
    }

    void append(short[] input) {
        if (input == null || input.length == 0) return;

        if (input.length >= maxSamples) {
            ensure(maxSamples);
            System.arraycopy(input, input.length - maxSamples, data, 0, maxSamples);
            start = 0;
            size = maxSamples;
            return;
        }

        int targetSize = Math.min(maxSamples, size + input.length);
        ensure(targetSize);

        int overflow = Math.max(0, size + input.length - maxSamples);
        if (overflow > 0) {
            start = (start + overflow) % data.length;
            size -= overflow;
        }

        int write = (start + size) % data.length;
        int first = Math.min(input.length, data.length - write);
        System.arraycopy(input, 0, data, write, first);
        int remaining = input.length - first;
        if (remaining > 0) System.arraycopy(input, first, data, 0, remaining);
        size += input.length;
    }

    int size() {
        return size;
    }

    short[] copy() {
        short[] out = new short[size];
        copyInto(out);
        return out;
    }

    private void ensure(int needed) {
        if (needed <= data.length) return;
        int nextSize = Math.min(maxSamples, Math.max(needed, data.length * 2));
        short[] next = new short[nextSize];
        copyInto(next);
        data = next;
        start = 0;
    }

    private void copyInto(short[] out) {
        if (size == 0) return;
        int first = Math.min(size, data.length - start);
        System.arraycopy(data, start, out, 0, first);
        int remaining = size - first;
        if (remaining > 0) System.arraycopy(data, 0, out, first, remaining);
    }
}
