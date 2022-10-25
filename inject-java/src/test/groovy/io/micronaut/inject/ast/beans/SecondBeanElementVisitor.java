package io.micronaut.inject.ast.beans;

import java.lang.annotation.Annotation;

import io.micronaut.core.order.Ordered;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class SecondBeanElementVisitor implements BeanElementVisitor<Annotation> {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public BeanElement visitBeanElement(BeanElement beanElement, VisitorContext visitorContext) {
        return beanElement;
    }
}
