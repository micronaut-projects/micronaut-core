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

class AnnotatingVisitor implements TypeElementVisitor<Object, Object> {


    @Override
    VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING
    }



    @Override
    void visitMethod(MethodElement element, VisitorContext context) {
        if (element.getOwningType().getName() != "example.Child") {
            return
        }

        element.annotate("test.CustomAnn")
    }
}
