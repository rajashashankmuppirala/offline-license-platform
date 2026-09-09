package com.enterprise.licensing.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

final class ConcurrentTestSupport {
    private ConcurrentTestSupport() {}
    static <T> List<T> race(int competitors, Callable<T> action) {
        var ready = new CountDownLatch(competitors); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(competitors)) {
            var futures = new ArrayList<java.util.concurrent.Future<T>>();
            for (int i=0;i<competitors;i++) futures.add(executor.submit(() -> { ready.countDown(); start.await(); return action.call(); }));
            ready.await(); start.countDown(); var values = new ArrayList<T>();
            for (var future : futures) values.add(future.get()); return List.copyOf(values);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
