package io.micronaut.aop.introduction.repeatable;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class MyRepoVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.getSimpleName().equals("saveAndFlush")) {
            element.annotate("test.MyDataMethod");
        }
    }
}
