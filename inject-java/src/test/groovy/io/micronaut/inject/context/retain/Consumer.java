package io.micronaut.inject.context.retain;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@Requires(property = "spec.name", value = "RetainedRegistrationsSpec")
public class Consumer {
    public static final AtomicInteger DESTROYED = new AtomicInteger();
    public final Expensive expensive;

    Consumer(Expensive expensive) {
        this.expensive = expensive;
    }

    @PreDestroy
    void close() {
        DESTROYED.incrementAndGet();
    }
}
