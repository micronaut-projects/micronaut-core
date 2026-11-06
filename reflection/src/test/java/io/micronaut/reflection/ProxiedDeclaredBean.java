package io.micronaut.reflection;

/**
 * A class overriding the accessor {@link ProxiedDeclared} declares
 * {@link io.micronaut.core.annotation.Introspected.Property}: the override hides the declaration, and carries no
 * annotation of its own, as method annotations are not inherited, so it is the accessor of no property.
 */
public class ProxiedDeclaredBean implements ProxiedDeclared {

    @Override
    public String value() {
        return "implemented";
    }
}
