package io.micronaut.reflection.other

import groovy.transform.PackageScope
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.reflection.Tag

/**
 * A generated introspection of another package declaring a package private method.
 */
@Introspected(hierarchy = true)
class GeneratedForeignAudit {
    @Executable
    @PackageScope
    @Tag("generated-foreign-audit")
    void audit(String value) {
    }
}
