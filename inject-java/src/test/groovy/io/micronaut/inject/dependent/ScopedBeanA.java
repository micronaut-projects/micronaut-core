package io.micronaut.inject.dependent;

import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Inject;

import jakarta.annotation.PreDestroy;

@Refreshable
public class ScopedBeanA {
    @Inject
    public BeanB beanBField;
    public final BeanB beanBConstructor;
    public BeanB beanBMethod;
    public boolean destroyed;

    public ScopedBeanA(BeanB beanBConstructor) {
        this.beanBConstructor = beanBConstructor;
    }

    @Inject
    public void setBeanBMethod(BeanB beanBMethod) {
        this.beanBMethod = beanBMethod;
    }

    @PreDestroy
    void stop() {
        TestData.DESTRUCTION_ORDER.add(ScopedBeanA.class.getSimpleName());
        this.destroyed = true;
    }
}
