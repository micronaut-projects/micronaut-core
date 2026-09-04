package io.micronaut.reflection

import io.micronaut.core.annotation.Introspected

/**
 * A bean asking for an index, so that the properties the index holds can be compared with the ones a generated
 * introspection indexes.
 */
@Introspected(indexed = [@Introspected.IndexedAnnotation(annotation = Tag, member = "value"),
    @Introspected.IndexedAnnotation(annotation = Hidden, member = "value")])
class IndexedParityBean {

    @Tag("first")
    String one

    @Tag("second")
    String two

    @Hidden("kept")
    String three
}
