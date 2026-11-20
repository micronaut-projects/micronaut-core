package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * Adds to {@code complete.Test} an {@code complete.Engine} built with {@link AnnotationValue#builder(String)} that
 * holds {@code complete.Cylinders} values built the same way, and keeps the occurrence the element then holds.
 *
 * @see NestedAnnotationValueDefaultsSpec
 */
class CompletingVisitor implements TypeElementVisitor<Object, Object> {

    static AnnotationValue<?> added

    static void reset() {
        added = null
    }

    @Override
    void visitClass(ClassElement element, VisitorContext context) {
        if (element.name != 'complete.Test') {
            return
        }
        def eight = AnnotationValue.builder('complete.Cylinders').member('value', 8).build()
        def two = AnnotationValue.builder('complete.Cylinders').member('value', 2).member('unit', 'pairs').build()
        def engine = AnnotationValue.builder('complete.Engine')
            .member('cylinders', eight)
            .member('spares', eight, two)
            .build()
        element.annotate(engine)
        added = element.getAnnotation('complete.Engine')
    }
}
