package io.micronaut.reflection;

/**
 * A generic interface declaring a property of the type it is given.
 *
 * @param <T> The type of the value
 */
public interface IntroBox<T> {

    T getValue();
}
