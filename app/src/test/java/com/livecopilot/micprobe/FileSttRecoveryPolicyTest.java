package com.livecopilot.micprobe;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class FileSttRecoveryPolicyTest {
    @Test
    public void cooldownStartsAfterRepeatedFullyDegradedTurns() {
        assertEquals(0L, FileSttRecoveryPolicy.cooldownMsForStreak(0));
        assertEquals(0L, FileSttRecoveryPolicy.cooldownMsForStreak(1));
        assertEquals(8_000L, FileSttRecoveryPolicy.cooldownMsForStreak(2));
        assertEquals(20_000L, FileSttRecoveryPolicy.cooldownMsForStreak(3));
        assertEquals(30_000L, FileSttRecoveryPolicy.cooldownMsForStreak(8));
    }

    @Test
    public void cooldownSuppressesOnlyExtraRetry() {
        assertTrue(FileSttRecoveryPolicy.allowRetry(10_000L, 0L));
        assertFalse(FileSttRecoveryPolicy.allowRetry(10_000L, 12_000L));
        assertTrue(FileSttRecoveryPolicy.allowRetry(12_000L, 12_000L));
    }

    @Test
    public void onlyTransientFailuresUseRetryBudget() {
        assertTrue(FileSttRecoveryPolicy.shouldRetryFailure(
                new IOException("reset"), 1, true));
        assertTrue(FileSttRecoveryPolicy.shouldRetryFailure(
                new IllegalStateException("STT 503"), 1, true));
        assertFalse(FileSttRecoveryPolicy.shouldRetryFailure(
                new IllegalStateException("STT 400"), 1, true));
        assertFalse(FileSttRecoveryPolicy.shouldRetryFailure(
                new IOException("reset"), 1, false));
        assertFalse(FileSttRecoveryPolicy.shouldRetryFailure(
                new IOException("reset"), 2, true));
    }

    @Test
    public void onlyFullyNetworkDegradedTurnsIncreaseStreak() {
        assertTrue(FileSttRecoveryPolicy.shouldDegradeTurn(2, 0, 1, 1));
        assertFalse(FileSttRecoveryPolicy.shouldDegradeTurn(2, 1, 1, 0));
        assertFalse(FileSttRecoveryPolicy.shouldDegradeTurn(2, 0, 0, 0));
    }

    @Test
    public void networkLikeClassificationCoversIoAndTransientHttp() {
        assertTrue(FileSttRecoveryPolicy.isNetworkLike(new IOException("reset")));
        assertTrue(FileSttRecoveryPolicy.isNetworkLike(new IllegalStateException("STT 429")));
        assertTrue(FileSttRecoveryPolicy.isNetworkLike(new IllegalStateException("STT 503")));
        assertFalse(FileSttRecoveryPolicy.isNetworkLike(new IllegalStateException("STT 400")));
        assertFalse(FileSttRecoveryPolicy.isNetworkLike(new IllegalArgumentException("bad json")));
    }
}
