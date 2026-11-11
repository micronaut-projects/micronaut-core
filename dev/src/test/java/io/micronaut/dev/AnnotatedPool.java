package io.micronaut.dev;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A parent-tier bean its module retains with {@link Retain}, made from the configuration under {@code my.pool}: stands
 * in for a connection pool.
 */
@Singleton
@Retain(invalidatedBy = "my.pool")
@Requires(property = "spec.name", value = "RetainAnnotationTest")
public class AnnotatedPool {
    public static final AtomicInteger CREATED = new AtomicInteger();
    public static final AtomicInteger DESTROYED = new AtomicInteger();
    public final String url;

    public AnnotatedPool(@Value("${my.pool.url:none}") String url) {
        this.url = url;
        CREATED.incrementAndGet();
    }

    @PreDestroy
    void close() {
        DESTROYED.incrementAndGet();
    }
}
