package io.micronaut.inject.ast.beans;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class TestBeanElementVisitor implements BeanElementVisitor<Prototype> {
    static BeanElement theBeanElement;
    static Boolean first;
    @Override
    public void visitBeanElement(BeanElement beanElement, VisitorContext visitorContext) {
        if (first == null) {
            first = SecondBeanElementVisitor.first == null;
        }
        theBeanElement = beanElement;
    }
}
