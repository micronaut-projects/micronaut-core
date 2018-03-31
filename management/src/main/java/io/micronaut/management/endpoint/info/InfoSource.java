package io.micronaut.management.endpoint.info;

import io.micronaut.context.env.PropertySource;
import io.micronaut.core.order.Ordered;
import org.reactivestreams.Publisher;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public interface InfoSource extends Ordered {

    Publisher<PropertySource> getSource();

    static <T> Supplier<T> cachedSupplier(Supplier<T> delegate) {
        AtomicReference<T> value = new AtomicReference<>();
        return () -> {
            T val = value.get();
            if (val == null) {
                synchronized(value) {
                    val = value.get();
                    if (val == null) {
                        val = Objects.requireNonNull(delegate.get());
                        value.set(val);
                    }
                }
            }
            return val;
        };
    }
}
