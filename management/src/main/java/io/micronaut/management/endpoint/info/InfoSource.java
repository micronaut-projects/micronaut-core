package io.micronaut.management.endpoint.info;

import io.micronaut.context.env.PropertySource;
import io.micronaut.core.order.Ordered;
import org.reactivestreams.Publisher;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public interface InfoSource extends Ordered {

    Publisher<PropertySource> getSource();

    static <T> Supplier<T> cachedSupplier(Supplier<T> actual) {
        return new Supplier<T>() {
            Supplier<T> delegate = this::initialize;
            boolean initialized;
            public T get() {
                return delegate.get();
            }
            private synchronized T initialize() {
                if (!initialized) {
                    T value = actual.get();
                    delegate = () -> value;
                    initialized = true;
                }
                return delegate.get();
            }
        };
    }
}
