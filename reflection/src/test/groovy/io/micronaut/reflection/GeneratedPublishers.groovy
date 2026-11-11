package io.micronaut.reflection

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.reflection.other.GeneratedForeignPublisher

/**
 * A generated introspection overriding the public method of a super class of another package.
 */
@Introspected(hierarchy = true)
class GeneratedForeignPublished extends GeneratedForeignPublisher {
    @Executable
    @Override
    @Tag("foreign-published")
    void publish(String value) {
    }
}

/**
 * A generated introspection of the package of its sub class.
 */
@Introspected(hierarchy = true)
class GeneratedLocalPublisher {
    @Executable
    @Tag("local-publisher")
    void publish(String value) {
    }
}

/**
 * A generated introspection overriding a method of a super class of its own package.
 */
@Introspected(hierarchy = true)
class GeneratedLocalPublished extends GeneratedLocalPublisher {
    @Executable
    @Override
    @Tag("local-published")
    void publish(String value) {
    }
}
