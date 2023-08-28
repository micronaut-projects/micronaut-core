package io.micronaut.aop.introduction.beans;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class MyRepoVisitor2 implements TypeElementVisitor<Object, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.getOwningType().getPackageName().equals("introducedbeanspec")) {
            element.annotate("introducedbeanspec.XMyDataMethod");
        } else if (element.getOwningType().getPackageName().equals("introducedbeanspec2")) {
            element.annotate("introducedbeanspec2.XMyDataMethod");
        }
    }
}
