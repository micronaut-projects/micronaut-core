package io.micronaut.inject.context.retain;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A listener its context creates for each bean it is told of, as a dependent of that bean.
 */
@Prototype
@Requires(property = "spec.name", value = "RetainedRegistrationsSpec")
@Requires(property = "retain.listener")
public class ExpensiveListener implements BeanCreatedEventListener<Expensive> {
    public static final AtomicInteger DESTROYED = new AtomicInteger();

    @Override
    public Expensive onCreated(BeanCreatedEvent<Expensive> event) {
        return event.getBean();
    }

    @PreDestroy
    void close() {
        DESTROYED.incrementAndGet();
    }
}
