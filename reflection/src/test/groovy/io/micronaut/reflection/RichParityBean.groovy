package io.micronaut.reflection

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

/**
 * A bean carrying the shapes the parity check is about: annotations with members of every kind, repeated
 * annotations, a stereotype, generic and array and enum property types, and inherited members.
 */
@Introspected
@Tag(value = "type", priority = 2, type = Number.class, level = Level.HIGH, nested = @Stereo(kind = "on-type"))
@Hidden("bean")
@Composed
class RichParityBean extends ParityBase<Integer> {

    @Tag(value = "one", priority = 3, type = String.class)
    @Tag(value = "two", level = Level.HIGH)
    String repeated

    @Tag("nested-generics")
    Map<String, List<Integer>> nested

    List<? extends Number> bounded

    int[] primitives

    String[] strings

    Optional<String> optional

    ParityColour colour

    Map<String, ParityBase<String>> generic

    @Hidden
    boolean flag

    @Executable
    @Tag("method")
    <E extends Number> Map<String, List<E>> combine(@Tag(value = "arg", priority = 9) List<? extends E> values, ParityColour colour) {
        return [:]
    }
}
