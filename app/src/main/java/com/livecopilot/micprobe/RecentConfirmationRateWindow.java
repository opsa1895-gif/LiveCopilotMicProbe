package com.livecopilot.micprobe;

import java.util.Arrays;

final class RecentConfirmationRateWindow {
    private final int[] outcomes;
    private int next;
    private int size;
    private int confirmations;

    RecentConfirmationRateWindow(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity");
        outcomes = new int[capacity];
    }

    void recordConfirmed() {
        record(true);
    }

    void recordReverted() {
        record(false);
    }

    int sampleCount() {
        return size;
    }

    int confirmationRatePercent(int minimumSamples) {
        if (minimumSamples <= 0) throw new IllegalArgumentException("minimumSamples");
        if (size < minimumSamples) return -1;
        return (int) (((long) confirmations * 100L) / size);
    }

    void clear() {
        Arrays.fill(outcomes, 0);
        next = 0;
        size = 0;
        confirmations = 0;
    }

    private void record(boolean confirmed) {
        int value = confirmed ? 1 : 0;
        if (size == outcomes.length) {
            confirmations -= outcomes[next];
        } else {
            size++;
        }
        outcomes[next] = value;
        confirmations += value;
        next = (next + 1) % outcomes.length;
    }
}
