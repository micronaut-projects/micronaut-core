package io.micronaut.reflection;

/**
 * A generic class declaring a method the implementations inherit without redeclaring, so that the only
 * declaration reflection finds under the name is the one of the super class.
 *
 * @param <T> The item type
 */
public class ExecStore<T> {

    @Tag("store")
    public T last() {
        return null;
    }
}
