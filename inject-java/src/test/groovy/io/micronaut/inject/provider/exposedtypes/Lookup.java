package io.micronaut.inject.provider.exposedtypes;

/**
 * One of the two generic types a {@link Handle} is exposed as.
 *
 * @param <T> The type looked up
 */
public interface Lookup<T> {

    String injectedAt();
}
