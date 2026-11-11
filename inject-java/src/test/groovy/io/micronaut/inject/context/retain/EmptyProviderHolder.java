package io.micronaut.inject.context.retain;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@Requires(property = "spec.name", value = "RetainedRegistrationsSpec")
public class EmptyProviderHolder {
    public static final AtomicInteger DESTROYED = new AtomicInteger();
    public final BeanProvider<Absent> absent;

    EmptyProviderHolder(BeanProvider<Absent> absent) {
        this.absent = absent;
    }

    @PreDestroy
    void close() {
        DESTROYED.incrementAndGet();
    }
}
