package io.micronaut.reflection;

/**
 * A generic interface declaring a method of its type variable and an overload of the same arity.
 *
 * @param <T> The item type
 */
public interface ExecRepo<T> {

    @Tag("repo")
    void save(@Tag("repo-param") T item);

    @Tag("repo-id")
    void save(@Tag("repo-id-param") Long id);
}
