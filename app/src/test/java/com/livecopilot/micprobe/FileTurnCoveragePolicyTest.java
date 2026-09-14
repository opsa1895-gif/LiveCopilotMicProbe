package com.livecopilot.micprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileTurnCoveragePolicyTest {
    @Test
    public void fullCoverageAllowsMainReply() {
        assertEquals(100, FileTurnCoveragePolicy.coveragePercent(3, 0));
        assertTrue(FileTurnCoveragePolicy.allowMainReply("готов въпрос", 3, 0, false));
    }

    @Test
    public void anyMissingChunkSuppressesMainReply() {
        assertEquals(75, FileTurnCoveragePolicy.coveragePercent(4, 1));
        assertFalse(FileTurnCoveragePolicy.allowMainReply("частична реплика", 4, 1, false));
    }

    @Test
    public void partialButWellCoveredTurnCanUseConservativeSemanticFallback() {
        assertEquals(75, FileTurnCoveragePolicy.coveragePercent(4, 1));
        assertTrue(FileTurnCoveragePolicy.allowSemanticFallback(
                false, "самостоятелен въпрос", 4, 1, false));
        assertTrue(FileTurnCoveragePolicy.isConservativeSemantic(4, 1, false));
    }

    @Test
    public void lowCoverageDoesNotSurfaceAReply() {
        assertEquals(50, FileTurnCoveragePolicy.coveragePercent(4, 2));
        assertFalse(FileTurnCoveragePolicy.allowSemanticFallback(
                false, "твърде непълна реплика", 4, 2, false));
    }

    @Test
    public void missingTailNeverUsesPartialSemanticReply() {
        assertFalse(FileTurnCoveragePolicy.allowSemanticFallback(
                false, "липсва краят", 5, 1, true));
        assertFalse(FileTurnCoveragePolicy.isConservativeSemantic(5, 1, true));
    }

    @Test
    public void completeNonActionableTurnStillKeepsNormalSemanticPathAvailable() {
        assertTrue(FileTurnCoveragePolicy.allowSemanticFallback(
                false, "цялата реплика", 2, 0, false));
        assertFalse(FileTurnCoveragePolicy.isConservativeSemantic(2, 0, false));
    }

    @Test
    public void coverageClampsInvalidFailureCounts() {
        assertEquals(0, FileTurnCoveragePolicy.coveragePercent(0, 0));
        assertEquals(100, FileTurnCoveragePolicy.coveragePercent(3, -2));
        assertEquals(0, FileTurnCoveragePolicy.coveragePercent(3, 9));
    }
}
