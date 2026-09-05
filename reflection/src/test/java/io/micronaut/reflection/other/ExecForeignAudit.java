package io.micronaut.reflection.other;

import io.micronaut.reflection.Tag;

/**
 * A type of another package declaring a package private method, which no type outside its package overrides.
 */
public class ExecForeignAudit {

    @Tag("foreign-audit")
    void audit(String value) {
        // empty on purpose - only the declaration is read
    }
}
