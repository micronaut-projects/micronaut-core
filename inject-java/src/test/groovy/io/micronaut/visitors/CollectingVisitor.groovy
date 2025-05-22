package io.micronaut.visitors

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Get
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.UnresolvedTypeKind
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class CollectingVisitor implements TypeElementVisitor<Object, Object> {

    static int numVisited = 0
    static int numMethodVisited = 0
    static boolean hasIntrospected
    static String getPath

    @Override
    void visitClass(ClassElement element, VisitorContext context) {
        if (element.getName() != "example.Child") {
            return
        }

        if (element.hasUnresolvedTypes(UnresolvedTypeKind.INTERFACE)) {
            throw new ElementPostponedToNextRoundException(element)
        }

        ++numVisited
        hasIntrospected = element.hasStereotype(Introspected)
    }

    @Override
    void visitMethod(MethodElement element, VisitorContext context) {
        if (element.getOwningType().getName() != "example.Child") {
            return
        }

        ++numMethodVisited
        getPath = element.stringValue(Get).orElse(null)
    }
}
