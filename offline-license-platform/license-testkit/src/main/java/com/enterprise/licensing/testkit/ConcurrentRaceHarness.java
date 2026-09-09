package com.enterprise.licensing.testkit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public final class ConcurrentRaceHarness {
    private ConcurrentRaceHarness() {
    }

    public static <T> List<T> race(int competitors, Callable<T> action) {
        var ready = new CountDownLatch(competitors);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(competitors)) {
            var futures = new ArrayList<java.util.concurrent.Future<T>>();
            for (int i = 0; i < competitors; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                }));
            }
            ready.await();
            start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get());
            return List.copyOf(results);
        } catch (Exception e) {
            throw new IllegalStateException("Concurrent race failed", e);
        }
    }
}
