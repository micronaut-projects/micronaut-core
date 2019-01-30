package io.micronaut.core.util.functional;

/**
 * Represents a supplier of results.
 *
 * <p>There is no requirement that a new or distinct result be returned each
 * time the supplier is invoked.
 *
 * <p>This is a functional interface
 * whose functional method is {@link #get()}.
 *
 * @param <T> the type of results supplied by this supplier
 * @param <E> the type of exception thrown from the supplier
 * @author James Kleeh
 * @since 1.1.0
 */
@FunctionalInterface
public interface ThrowingSupplier<T, E extends Throwable> {

    /**
     * Gets a result.
     *
     * @throws E the type of exception that can be thrown
     * @return a result
     */
    T get() throws E;
}
