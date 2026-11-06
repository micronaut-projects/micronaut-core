package io.micronaut.reflection;

import io.micronaut.core.annotation.Introspected;

/**
 * An interface declaring an accessor whose name carries no accessor prefix: {@link Introspected.Property} makes it
 * the accessor of the property named after the method.
 */
public interface ProxiedDeclared {

    @Introspected.Property
    @Tag("declared")
    String value();
}
