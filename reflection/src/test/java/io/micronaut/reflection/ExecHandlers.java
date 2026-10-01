package io.micronaut.reflection;

/**
 * A type declaring two methods of the same name and arity, one of its type variable and one of a type, and a
 * static method.
 *
 * @param <T> The event type
 */
public class ExecHandlers<T> {

    @Tag("static")
    public static void register(String name) {
        // empty on purpose - only the declaration is read
    }

    @Tag("event")
    public void on(T event) {
        // empty on purpose - only the declaration is read
    }

    @Tag("number")
    public void on(Number number) {
        // empty on purpose - only the declaration is read
    }
}
