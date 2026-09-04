package io.micronaut.reflection

import io.micronaut.core.annotation.Introspected

import static io.micronaut.reflection.DeepAliasAnnotations.DeepA

/**
 * A bean the processor describes, carrying a composed contract, so that the metadata the processor generates
 * for it can be compared with the metadata {@link ReflectionAnnotations} builds for the same members.
 */
@Introspected
class RetainedBean {

    @Username(min = 8)
    String name

    @Sized(min = 2, max = 4)
    String code

    @Spanned(first = 7, second = 9)
    String spanned

    @Spanned
    String spannedByDefault

    @Labelled("shortcut")
    String labelled

    @Spread(first = 7, second = 9)
    String spread

    @Tag(value = "one", priority = 3, type = String, level = Level.HIGH, nested = @Stereo(kind = "in"))
    @Tag("two")
    String tagged

    @Sized
    String bare

    String plain

    @Tag("same")
    @Tag("same")
    String duplicated

    @Tags(value = [@Tag("one"), @Tag("two")], note = "kept-note")
    String explicitTags

    @Tags(value = [], note = "empty-note")
    String emptyTags

    @DeepA(shortest = 7)
    String deepAlias
}
