package com.livecopilot.micprobe;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LatestWinsExecutorTest {
    @Test
    public void newestQueuedTaskReplacesOlderQueuedTask() throws Exception {
        LatestWinsExecutor executor = new LatestWinsExecutor(2);
        CountDownLatch workersStarted = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        CountDownLatch newestRan = new CountDownLatch(1);
        AtomicBoolean oldQueuedRan = new AtomicBoolean(false);

        Runnable blocker = () -> {
            workersStarted.countDown();
            try {
                releaseWorkers.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            executor.execute(blocker);
            executor.execute(blocker);
            assertTrue(workersStarted.await(2, TimeUnit.SECONDS));

            executor.execute(() -> oldQueuedRan.set(true));
            executor.execute(newestRan::countDown);

            releaseWorkers.countDown();
            assertTrue(newestRan.await(2, TimeUnit.SECONDS));
            Thread.sleep(80L);
            assertFalse(oldQueuedRan.get());
        } finally {
            releaseWorkers.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void singleWorkerAlsoKeepsOnlyNewestPendingTask() throws Exception {
        LatestWinsExecutor executor = new LatestWinsExecutor(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch newestRan = new CountDownLatch(1);
        AtomicBoolean oldQueuedRan = new AtomicBoolean(false);

        try {
            executor.execute(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            executor.execute(() -> oldQueuedRan.set(true));
            executor.execute(newestRan::countDown);

            releaseFirst.countDown();
            assertTrue(newestRan.await(2, TimeUnit.SECONDS));
            Thread.sleep(80L);
            assertFalse(oldQueuedRan.get());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }
}
