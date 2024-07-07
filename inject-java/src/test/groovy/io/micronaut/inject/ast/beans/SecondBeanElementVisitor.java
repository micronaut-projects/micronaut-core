package io.micronaut.inject.ast.beans;

import java.lang.annotation.Annotation;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class SecondBeanElementVisitor implements BeanElementVisitor<Annotation> {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public BeanElement visitBeanElement(@NonNull BeanElement beanElement, @NonNull VisitorContext visitorContext) {
        return beanElement;
    }
}
