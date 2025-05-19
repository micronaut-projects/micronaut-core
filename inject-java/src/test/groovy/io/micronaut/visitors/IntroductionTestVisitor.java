package io.micronaut.visitors;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.UnresolvedTypeKind;
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class IntroductionTestVisitor implements TypeElementVisitor<IntroductionTest, Object> {

    @Override
    public VisitorKind getVisitorKind() {
        return TypeElementVisitor.super.getVisitorKind();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(IntroductionTest.class)) {
            if (element.hasUnresolvedTypes(UnresolvedTypeKind.INTERFACE)) {
                throw new ElementPostponedToNextRoundException(element);
            }
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        ClassElement owningType = element.getOwningType();
        if (owningType.hasDeclaredAnnotation(IntroductionTest.class)) {
            element.annotate("SomeAnnotation");
        }
    }
}
