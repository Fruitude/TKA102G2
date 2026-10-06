package com.fruitude.product.model;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrontCatalogCacheTest {
    static class MutableClock extends Clock {
        long now;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
    @Test public void reusesResultForTenMinutesAndRefreshesAtExpiry() {
        MutableClock clock = new MutableClock();
        FrontCatalogCache cache = new FrontCatalogCache(clock, Duration.ofMinutes(10));
        AtomicInteger queries = new AtomicInteger();
        var loader = (java.util.function.Supplier<List<FrontCatalogService.ProductView>>) () -> { queries.incrementAndGet(); return List.of(); };
        cache.get(loader); clock.now = 599999; cache.get(loader);
        assertEquals(1, queries.get());
        clock.now = 600000; cache.get(loader); assertEquals(2, queries.get());
    }
    @Test public void concurrentRequestsShareOneLoad() throws Exception {
        FrontCatalogCache cache = new FrontCatalogCache(Clock.systemUTC(), Duration.ofMinutes(10));
        AtomicInteger queries = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Object>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) tasks.add(() -> cache.get(() -> { queries.incrementAndGet(); return List.of(); }));
            for (Future<Object> result : pool.invokeAll(tasks)) result.get();
            assertEquals(1, queries.get());
        } finally { pool.shutdownNow(); }
    }
    @Test public void failedLoadDoesNotCacheFailure() {
        FrontCatalogCache cache = new FrontCatalogCache(Clock.systemUTC(), Duration.ofMinutes(10));
        try { cache.get(() -> { throw new IllegalStateException("database unavailable"); }); fail(); }
        catch (IllegalStateException expected) {}
        assertTrue(cache.get(List::of).isEmpty());
    }
}
