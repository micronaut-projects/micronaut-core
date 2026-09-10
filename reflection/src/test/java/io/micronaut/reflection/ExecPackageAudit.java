package io.micronaut.reflection;

/**
 * A type declaring a package private method, which a type of its own package can override.
 */
public class ExecPackageAudit {

    @Tag("package-audit")
    void audit(String value) {
        // empty on purpose - only the declaration is read
    }
}
