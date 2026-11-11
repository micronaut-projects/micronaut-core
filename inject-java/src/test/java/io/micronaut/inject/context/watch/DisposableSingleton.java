package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@Requires(property = "spec.name", value = "BeanWatchTest")
public class DisposableSingleton implements Disposable {
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
