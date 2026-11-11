package io.micronaut.inject.provider.exposedtypes;

/**
 * The other of the two generic types a {@link Handle} is exposed as.
 *
 * @param <E> The type of the elements, named differently from the type variable of {@link Lookup}
 */
public interface Source<E> {

    boolean isClosed();
}
