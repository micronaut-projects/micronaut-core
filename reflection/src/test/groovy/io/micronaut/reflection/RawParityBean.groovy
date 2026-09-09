package io.micronaut.reflection

import io.micronaut.core.annotation.Introspected

class RawParityBox<B extends Number> {
}

/**
 * A bean whose properties are written raw, with a type variable, and with type arguments, so that the
 * description the processor generates and the reflective one can be compared on which of them are raw.
 */
@Introspected
class RawParityBean<T extends Number> {

    List raw
    List<T> variable
    List<String> concrete
    List<Object> object
    RawParityBox box
    Map<String, List> nested
}
