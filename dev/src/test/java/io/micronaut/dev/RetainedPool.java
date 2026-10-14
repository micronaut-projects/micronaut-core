package io.micronaut.dev;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A parent-tier bean the manifest retains across restarts: stands in for a connection pool.
 */
@Singleton
@Requires(property = "spec.name", value = "DevRuntimeTest")
public class RetainedPool {
    public static final AtomicInteger CREATED = new AtomicInteger();
    public static final AtomicInteger DESTROYED = new AtomicInteger();
    public final int number = CREATED.incrementAndGet();

    @PreDestroy
    void close() {
        DESTROYED.incrementAndGet();
    }
}
