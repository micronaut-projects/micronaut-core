package io.micronaut.reflection

import io.micronaut.core.annotation.Introspected

/**
 * An interface declaring the accessors its sub interface inherits, the generated side of the hierarchy a
 * {@code ProxyIntrospectionSpec} case reads reflectively.
 */
@Introspected
interface ProxiedParityContract {

    @Tag("contract-string")
    String getString()

    @Tag("contract-integer")
    Integer getInteger()
}

/**
 * An interface declaring nothing of its own: every property of it is inherited.
 */
@Introspected
interface ProxiedParitySub extends ProxiedParityContract {
}

/**
 * An interface declaring an accessor whose name carries no accessor prefix, the generated side of
 * {@code ProxiedDeclared}: {@link Introspected.Property} makes it the accessor of the property named after the
 * method, of every type inheriting it.
 */
@Introspected
interface ProxiedParityDeclared {

    @Introspected.Property
    @Tag("parity-declared")
    String value()
}

/**
 * An interface inheriting the declared accessor, declaring nothing of its own.
 */
@Introspected
interface ProxiedParityDeclaredSub extends ProxiedParityDeclared {
}

/**
 * An abstract class inheriting the declared accessor, overriding nothing.
 */
@Introspected
abstract class ProxiedParityDeclaredBase implements ProxiedParityDeclared {
}

/**
 * A class overriding the declared accessor: the override hides the declaration, and carries no annotation of its
 * own, as method annotations are not inherited.
 */
@Introspected
class ProxiedParityDeclaredBean implements ProxiedParityDeclared {

    @Override
    String value() {
        return "implemented"
    }
}
