package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.atomic.AtomicInteger;

@Prototype
@Requires(property = "spec.name", value = "BeanWatchTest")
public class DisposablePrototype implements Disposable {
    static final AtomicInteger DESTROYED = new AtomicInteger();

    @Override
    public AtomicInteger destroyed() {
        return DESTROYED;
    }

    @PreDestroy
    void destroy() {
        DESTROYED.incrementAndGet();
    }
}
