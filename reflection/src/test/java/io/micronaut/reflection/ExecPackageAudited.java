package io.micronaut.reflection;

/**
 * A type of the package of the super class, so its method does override the package private one.
 */
public class ExecPackageAudited extends ExecPackageAudit {

    @Override
    @Tag("audited")
    void audit(String value) {
    }
}
