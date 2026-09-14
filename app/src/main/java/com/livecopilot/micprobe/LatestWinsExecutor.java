package com.livecopilot.micprobe;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class LatestWinsExecutor {
    private final ThreadPoolExecutor executor;

    LatestWinsExecutor(int parallelism) {
        int workers = Math.max(1, parallelism);
        executor = new ThreadPoolExecutor(
                workers,
                workers,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.allowCoreThreadTimeOut(true);
    }

    void execute(Runnable task) {
        if (task == null || executor.isShutdown()) return;
        executor.execute(task);
    }

    void shutdownNow() {
        executor.shutdownNow();
    }
}
