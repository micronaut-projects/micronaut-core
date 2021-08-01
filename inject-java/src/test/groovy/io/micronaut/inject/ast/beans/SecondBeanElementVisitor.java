package io.micronaut.inject.ast.beans;

import java.lang.annotation.Annotation;

import io.micronaut.core.order.Ordered;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class SecondBeanElementVisitor implements BeanElementVisitor<Annotation> {
    static Boolean first;

    public SecondBeanElementVisitor() {
        first = null;
    }

    @Override
    public void visitBeanElement(BeanElement beanElement, VisitorContext visitorContext) {
        if (first == null) {
            first = TestBeanElementVisitor.first == null;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
