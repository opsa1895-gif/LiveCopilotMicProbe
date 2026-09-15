package com.livecopilot.micprobe;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SemanticFallbackPolicyTest {
    @Test
    public void partialInputStaysDirectOnly() {
        assertFalse(SemanticFallbackPolicy.shouldGenerateVariants(true));
        assertTrue(SemanticFallbackPolicy.shouldGenerateVariants(false));
    }

    @Test
    public void transientHttpFailuresRetryOnce() {
        assertTrue(SemanticFallbackPolicy.shouldRetryHttp(408, 1));
        assertTrue(SemanticFallbackPolicy.shouldRetryHttp(429, 1));
        assertTrue(SemanticFallbackPolicy.shouldRetryHttp(500, 1));
        assertTrue(SemanticFallbackPolicy.shouldRetryHttp(503, 1));
    }

    @Test
    public void permanentHttpFailuresDoNotRetry() {
        assertFalse(SemanticFallbackPolicy.shouldRetryHttp(400, 1));
        assertFalse(SemanticFallbackPolicy.shouldRetryHttp(401, 1));
        assertFalse(SemanticFallbackPolicy.shouldRetryHttp(404, 1));
    }

    @Test
    public void retryBudgetStopsAfterSecondAttempt() {
        assertFalse(SemanticFallbackPolicy.shouldRetryHttp(503, 2));
        assertFalse(SemanticFallbackPolicy.shouldRetryFailure(new IOException("timeout"), 2));
    }

    @Test
    public void onlyIoFailuresAreRetryable() {
        assertTrue(SemanticFallbackPolicy.shouldRetryFailure(new IOException("reset"), 1));
        assertFalse(SemanticFallbackPolicy.shouldRetryFailure(new IllegalStateException("bad json"), 1));
        assertFalse(SemanticFallbackPolicy.shouldRetryFailure(null, 1));
    }

    @Test
    public void retryDelayIsSmallAndBounded() {
        assertEquals(0L, SemanticFallbackPolicy.retryDelayMs(1));
        assertEquals(250L, SemanticFallbackPolicy.retryDelayMs(2));
        assertEquals(250L, SemanticFallbackPolicy.retryDelayMs(9));
    }
}
