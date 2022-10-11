package io.micronaut.inject.dependent;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class PrototypeBeanA {
    @Inject public BeanB beanBField;
    public final BeanB beanBConstructor;
    public BeanB beanBMethod;
    @Inject public List<InterfaceA> collection;

    public PrototypeBeanA(BeanB beanBConstructor) {
        this.beanBConstructor = beanBConstructor;
    }

    @Inject
    public void setBeanBMethod(BeanB beanBMethod) {
        this.beanBMethod = beanBMethod;
    }
}
