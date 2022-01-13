package io.micronaut.inject.visitor

import io.micronaut.inject.ast.MethodElement

class MutatingVisitor implements TypeElementVisitor<Object, SomeAnn> {

    void visitMethod(MethodElement element, VisitorContext context) {
        element.annotate("my.custom.Annotation")
        element.annotationMetadata.findAnnotation(SomeAnn).get().getRequiredValue("someValue", String)
    }
}
