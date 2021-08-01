package io.micronaut.inject.ast.beans;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class TestBeanElementVisitor implements BeanElementVisitor<Prototype> {
    static BeanElement theBeanElement;

    @Override
    public void visitBeanElement(BeanElement beanElement, VisitorContext visitorContext) {
        theBeanElement = beanElement;
    }
}
