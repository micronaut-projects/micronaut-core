package io.micronaut.inject.context.retain;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@Requires(property = "spec.name", value = "RetainedRegistrationsSpec")
public class Expensive {
    public static final AtomicInteger CREATED = new AtomicInteger();
    public static final AtomicInteger DESTROYED = new AtomicInteger();
    public final Helper helper;
    public final Scratch scratch;

    public Expensive(Helper helper, Scratch scratch) {
        this.helper = helper;
        this.scratch = scratch;
        CREATED.incrementAndGet();
    }

    @PreDestroy
    void close() {
        DESTROYED.incrementAndGet();
    }
}
