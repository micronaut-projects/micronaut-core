package io.micronaut.context.generics;

/**
 * Passes its own unbound variable to the super type.
 *
 * @param <T> The repository type
 */
public class OpenRepo<T> implements Repo<T> {
}
