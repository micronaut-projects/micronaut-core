package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * Builds an {@code complete.Engine} holding {@code complete.Cylinders} values, completes it and adds it to
 * {@code complete.Test}.
 *
 * @see CompleteAnnotationValueSpec
 */
class CompletingVisitor implements TypeElementVisitor<Object, Object> {

    static AnnotationValue<?> completed
    static AnnotationValue<?> completedAgain
    static AnnotationValue<?> unresolved
    static AnnotationValue<?> unresolvedCompleted

    static void reset() {
        completed = null
        completedAgain = null
        unresolved = null
        unresolvedCompleted = null
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
        completed = context.completeAnnotationValue(engine)
        completedAgain = context.completeAnnotationValue(completed)
        unresolved = AnnotationValue.builder('complete.Missing').member('value', 1).build()
        unresolvedCompleted = context.completeAnnotationValue(unresolved)
        element.annotate(engine)
    }
}
