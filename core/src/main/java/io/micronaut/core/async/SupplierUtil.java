package io.micronaut.core.async;

import java.util.function.Supplier;

/**
 * Helper methods for dealing with {@link Supplier}
 *
 * @author James Kleeh
 * @since 1.0
 */
public class SupplierUtil {

    /**
     * Caches the result of supplier in a thread safe manner.
     *
     * @param actual The supplier providing the result
     * @param <T> The type of result
     * @return A new supplier that will cache the result
     */
    public static <T> Supplier<T> memoized(Supplier<T> actual) {
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
