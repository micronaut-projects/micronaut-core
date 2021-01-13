package io.micronaut.context;

/**
 * A provider contract.
 *
 * @param <T> The type
 * @author James Kleeh
 * @since 2.3.0
 */
public interface Provider<T> {

    /**
     * @return A fully-constructed and injected instance of T.
     */
    T get();
}
