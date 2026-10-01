package io.micronaut.reflection;

/**
 * An interface with a private method, which is no method of the types inheriting it: not of a class implementing
 * it, and not of a {@link java.lang.reflect.Proxy} standing for it.
 */
public interface ProxiedHelper {

    String getName();

    default String describe() {
        return helper();
    }

    private String helper() {
        return "helped " + getName();
    }
}
