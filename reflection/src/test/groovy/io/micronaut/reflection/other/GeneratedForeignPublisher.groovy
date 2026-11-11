package io.micronaut.reflection.other

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.reflection.Tag

/**
 * A generated introspection of another package declaring a public method.
 */
@Introspected(hierarchy = true)
class GeneratedForeignPublisher {
    @Executable
    @Tag("foreign-publisher")
    void publish(String value) {
    }
}
