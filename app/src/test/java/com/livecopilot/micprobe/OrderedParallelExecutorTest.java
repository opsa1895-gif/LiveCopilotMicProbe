package com.livecopilot.micprobe;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OrderedParallelExecutorTest {
    @Test
    public void workRunsInParallelButCommitsInSubmissionOrder() throws Exception {
        OrderedParallelExecutor<Integer> executor = new OrderedParallelExecutor<>(2);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        CountDownLatch committed = new CountDownLatch(2);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());

        try {
            executor.submit(() -> {
                bothStarted.countDown();
                releaseFirst.await(2, TimeUnit.SECONDS);
                return 1;
            }, (result, error) -> {
                order.add(result);
                committed.countDown();
            });
            executor.submit(() -> {
                bothStarted.countDown();
                releaseSecond.await(2, TimeUnit.SECONDS);
                return 2;
            }, (result, error) -> {
                order.add(result);
                committed.countDown();
            });

            assertTrue(bothStarted.await(2, TimeUnit.SECONDS));
            releaseSecond.countDown();
            Thread.sleep(80L);
            assertTrue(order.isEmpty());
            releaseFirst.countDown();
            assertTrue(committed.await(2, TimeUnit.SECONDS));
            assertEquals(java.util.Arrays.asList(1, 2), order);
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void markerRunsAfterEarlierCommits() throws Exception {
        OrderedParallelExecutor<Integer> executor = new OrderedParallelExecutor<>(2);
        CountDownLatch done = new CountDownLatch(1);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());

        try {
            executor.submit(() -> 1, (result, error) -> order.add(result));
            executor.submit(() -> 2, (result, error) -> order.add(result));
            executor.afterSubmitted(() -> {
                order.add(3);
                done.countDown();
            });

            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals(java.util.Arrays.asList(1, 2, 3), order);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void expiredHeadDoesNotBlockLaterCommitOrMarker() throws Exception {
        OrderedParallelExecutor<Integer> executor = new OrderedParallelExecutor<>(2);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        try {
            executor.submit(() -> {
                releaseSlow.await(5, TimeUnit.SECONDS);
                return 1;
            }, (result, error) -> {
                order.add(1);
                errors.add(error);
            }, 120L);
            executor.submit(() -> 2, (result, error) -> {
                order.add(result);
                errors.add(error);
            }, 1_000L);
            executor.afterSubmitted(() -> {
                order.add(3);
                done.countDown();
            });

            assertTrue(done.await(1, TimeUnit.SECONDS));
            assertEquals(java.util.Arrays.asList(1, 2, 3), order);
            assertEquals(2, errors.size());
            assertTrue(errors.get(0) instanceof TimeoutException);
            assertNull(errors.get(1));
        } finally {
            releaseSlow.countDown();
            executor.shutdownNow();
        }
    }
}
