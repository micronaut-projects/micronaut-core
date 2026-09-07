package io.micronaut.reflection.other

/**
 * A super class in another package: what it keeps to its sub classes a type in another package cannot reach
 * without reflection, and the processors do not describe it.
 */
class OtherBase {

    String getOpen() {
        return "open"
    }

    protected String getGuarded() {
        return "guarded"
    }
}
