package io.micronaut.inject.ast.beans;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class TestBeanElementVisitor implements BeanElementVisitor<Prototype> {
    public BeanElement theBeanElement;
    public boolean initialized = false;
    public boolean terminated = false;


    @Override
    public void visitBeanElement(BeanElement beanElement, VisitorContext visitorContext) {
        Element producingElement = beanElement.getProducingElement();
        if (producingElement instanceof MemberElement) {
            producingElement = ((MemberElement) producingElement).getDeclaringType();
        }
        if (producingElement.getName().startsWith("testbe")) {
            theBeanElement = beanElement;
        }
    }

    @Override
    public void start(VisitorContext visitorContext) {
        theBeanElement = null;
        initialized = true;
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        if (theBeanElement != null) {

            visitorContext.getClassElement(String.class)
                    .ifPresent(e -> theBeanElement.addAssociatedBean(e)
                            .createWith(
                                    e.getEnclosedElement(
                                            ElementQuery.of(ConstructorElement.class)
                                                    .filter(ce -> ce.hasParameters() && ce.getParameters()[0].getType().isAssignable(String.class))
                                    )
                                            .orElseThrow(() -> new IllegalStateException("Unknown constructor"))
                            )
                            .withParameters((params) -> params[0].injectValue("test")));
        }
        terminated = true;
    }
}
