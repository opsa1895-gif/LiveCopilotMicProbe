package com.livecopilot.micprobe;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class OrderedParallelExecutor<T> {
    interface Work<T> {
        T run() throws Exception;
    }

    interface Commit<T> {
        void accept(T result, Throwable error);
    }

    private final ExecutorService workers;
    private final ExecutorService committer = Executors.newSingleThreadExecutor();
    private final Set<Future<?>> futures = new HashSet<>();
    private boolean shutdown;

    OrderedParallelExecutor(int parallelism) {
        workers = Executors.newFixedThreadPool(Math.max(1, parallelism));
    }

    synchronized void submit(Work<T> work, Commit<T> commit) {
        submit(work, commit, 0L);
    }

    synchronized void submit(Work<T> work, Commit<T> commit, long maxWaitMs) {
        if (shutdown || work == null || commit == null) return;
        long boundedWaitMs = Math.max(0L, maxWaitMs);
        long deadlineNanos = boundedWaitMs > 0L
                ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(boundedWaitMs)
                : Long.MAX_VALUE;
        Future<T> future = workers.submit(work::run);
        futures.add(future);
        committer.execute(() -> {
            T result = null;
            Throwable error = null;
            try {
                if (boundedWaitMs > 0L) {
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    if (remainingNanos <= 0L) {
                        throw new TimeoutException("ordered work deadline exceeded");
                    }
                    result = future.get(remainingNanos, TimeUnit.NANOSECONDS);
                } else {
                    result = future.get();
                }
            } catch (TimeoutException timeout) {
                future.cancel(true);
                error = timeout;
            } catch (CancellationException cancelled) {
                removeFuture(future);
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                removeFuture(future);
                return;
            } catch (ExecutionException execution) {
                error = execution.getCause() == null ? execution : execution.getCause();
            }
            removeFuture(future);
            try {
                commit.accept(result, error);
            } catch (Throwable ignored) {}
        });
    }

    synchronized void afterSubmitted(Runnable task) {
        if (shutdown || task == null) return;
        committer.execute(task);
    }

    synchronized void cancelPending() {
        if (futures.isEmpty()) return;
        for (Future<?> future : new HashSet<>(futures)) {
            future.cancel(true);
        }
        futures.clear();
    }

    synchronized void shutdownNow() {
        if (shutdown) return;
        shutdown = true;
        cancelPending();
        workers.shutdownNow();
        committer.shutdownNow();
    }

    private synchronized void removeFuture(Future<?> future) {
        futures.remove(future);
    }
}
