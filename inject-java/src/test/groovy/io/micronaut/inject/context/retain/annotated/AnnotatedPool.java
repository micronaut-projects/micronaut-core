package io.micronaut.inject.context.retain.annotated;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@Retain
@Requires(property = "spec.name", value = "RetainAnnotationSpec")
public class AnnotatedPool {
    public static final AtomicInteger CREATED = new AtomicInteger();
    public static final AtomicInteger DESTROYED = new AtomicInteger();

    public AnnotatedPool() {
        CREATED.incrementAndGet();
    }

    @PreDestroy
    void close() {
        DESTROYED.incrementAndGet();
    }
}
