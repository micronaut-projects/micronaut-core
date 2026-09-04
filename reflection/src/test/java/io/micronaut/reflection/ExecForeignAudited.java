package io.micronaut.reflection;

import io.micronaut.reflection.other.ExecForeignAudit;

/**
 * A type of another package than the super class, so its method hides the package private one of the super
 * class rather than overriding it.
 */
public class ExecForeignAudited extends ExecForeignAudit {

    @Tag("foreign-audited")
    public void audit(String value) {
        // empty on purpose - only the declaration is read
    }
}
