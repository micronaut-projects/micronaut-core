package io.micronaut.context.generics;

/**
 * A generic super class that binds the interface's argument to its own variable.
 *
 * @param <X> The number type
 */
public abstract class NumberRepo<X extends Number> implements Repo<X> {
}
