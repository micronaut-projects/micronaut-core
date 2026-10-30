package io.micronaut.inject.context.retain;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.atomic.AtomicInteger;

@Prototype
@Requires(property = "spec.name", value = "RetainedRegistrationsSpec")
public class Scratch {
    public static final AtomicInteger DESTROYED = new AtomicInteger();

    @PreDestroy
    void close() {
        DESTROYED.incrementAndGet();
    }
}
