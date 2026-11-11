package io.micronaut.inject.provider.exposedtypes;

/**
 * A bean built for the injection point it is injected into, exposed as both a {@link Lookup} and a {@link Source},
 * and closed when the bean it is injected into is destroyed.
 *
 * @param <T> The type argument of the injection point
 */
public final class Handle<T> implements Lookup<T>, Source<T>, AutoCloseable {

    private final String injectedAt;
    private boolean closed;

    Handle(String injectedAt) {
        this.injectedAt = injectedAt;
    }

    @Override
    public String injectedAt() {
        return injectedAt;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
