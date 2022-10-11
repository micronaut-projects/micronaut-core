package io.micronaut.inject.dependent.listeners;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class AnotherSingletonBeanA {
    @Inject public AnotherBeanB beanBField;
    public final AnotherBeanB beanBConstructor;
    public AnotherBeanB beanBMethod;
    public boolean destroyed;
    @Inject public List<AnotherInterfaceA> collection;

    public AnotherSingletonBeanA(AnotherBeanB beanBConstructor) {
        this.beanBConstructor = beanBConstructor;
    }

    @Inject
    public void setBeanBMethod(AnotherBeanB beanBMethod) {
        this.beanBMethod = beanBMethod;
    }

}
