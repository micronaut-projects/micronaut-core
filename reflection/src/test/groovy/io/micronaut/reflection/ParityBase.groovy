package io.micronaut.reflection

/**
 * A generic supertype, so that the parity check covers the members a subtype inherits and the type
 * arguments it fixes.
 *
 * @param <T> the type the subtype fixes
 */
@Tag(value = "base", priority = 5)
abstract class ParityBase<T> {

    @Tag("inherited-field")
    T identifier

    @Hidden("base")
    String note

    T getIdentifier() {
        return identifier
    }

    void setIdentifier(T identifier) {
        this.identifier = identifier
    }
}
