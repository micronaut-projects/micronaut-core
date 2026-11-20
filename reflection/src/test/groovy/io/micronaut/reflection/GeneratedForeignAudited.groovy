package io.micronaut.reflection

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.reflection.other.GeneratedForeignAudit

/**
 * A generated introspection hiding the package private method of a super class of another package.
 */
@Introspected(hierarchy = true)
class GeneratedForeignAudited extends GeneratedForeignAudit {
    @Executable
    @Tag("generated-foreign-audited")
    void audit(String value) {
    }
}
