package com.fruitude.product.model;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/** One immutable catalog per application instance; single loader on expiry. */
final class FrontCatalogCache {
    private final Clock clock;
    private final long ttl;
    private List<FrontCatalogService.ProductView> value;
    private long expiresAt;
    FrontCatalogCache(Clock clock, Duration ttl) { this.clock = clock; this.ttl = ttl.toMillis(); }
    synchronized List<FrontCatalogService.ProductView> get(Supplier<List<FrontCatalogService.ProductView>> loader) {
        if (value == null || clock.millis() >= expiresAt) {
            List<FrontCatalogService.ProductView> loaded = List.copyOf(loader.get());
            value = loaded;
            expiresAt = clock.millis() + ttl;
        }
        return value;
    }
}
