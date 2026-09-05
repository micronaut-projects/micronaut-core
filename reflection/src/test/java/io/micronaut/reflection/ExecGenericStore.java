package io.micronaut.reflection;

/**
 * A generic super class declaring a method of its type variable.
 *
 * @param <T> The item type
 */
public abstract class ExecGenericStore<T> {

    @Tag("store")
    public void keep(@Tag("store-param") T item) {
    }
}
