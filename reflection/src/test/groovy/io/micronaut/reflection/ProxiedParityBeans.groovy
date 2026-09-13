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
