package io.micronaut.inject.context.retain.resolver;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A prototype the connection resolves through its resolver and owns: destroyed with the connection.
 */
@Prototype
@Requires(property = "spec.name", value = "RetainedResolverSpec")
public class Channel {
    public static final AtomicInteger CLOSED = new AtomicInteger();

    @PreDestroy
    void close() {
        CLOSED.incrementAndGet();
    }
}
