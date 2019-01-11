package io.micronaut.web.router.version.resolution;

import java.util.Optional;

/**
 * Resolves a version from a given object.
 *
 * @param <T> The type of object to resolve from
 * @param <R> The type of version returned
 */
public interface VersionResolver<T, R> {

    /**
     * Resolves a version from a given object.
     *
     * @param object The type of object to resolve from
     * @return An optional object representing the version
     */
    Optional<R> resolve(T object);

}

