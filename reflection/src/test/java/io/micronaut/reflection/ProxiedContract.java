package io.micronaut.reflection;

/**
 * An interface declaring the accessors of two properties, stood for by a {@link java.lang.reflect.Proxy} in
 * {@code ProxyIntrospectionSpec}: the constraints a specification reads are declared here and nowhere else.
 */
public interface ProxiedContract {

    @Sized(min = 2)
    String getString();

    @Tag("integer")
    Integer getInteger();
}
