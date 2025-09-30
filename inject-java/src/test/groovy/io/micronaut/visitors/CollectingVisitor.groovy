package io.micronaut.visitors

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Controller
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
    static String controllerPath

    @Override
    VisitorKind getVisitorKind() {
        return VisitorKind.AGGREGATING
    }

    @Override
    void start(VisitorContext visitorContext) {
        numVisited = 0
        numMethodVisited = 0
    }

    @Override
    void visitClass(ClassElement element, VisitorContext context) {
        if (element.getName() != "example.Child") {
            return
        }

        if (element.hasUnresolvedTypes(UnresolvedTypeKind.INTERFACE)) {
            throw new ElementPostponedToNextRoundException(element)
        }

        controllerPath = element.stringValue(Controller.class).orElse(null)
        if ("<error>".equalsIgnoreCase(controllerPath)) {
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

        def path = element.stringValue(Get).orElse(null)
        getPath = path
    }
}
