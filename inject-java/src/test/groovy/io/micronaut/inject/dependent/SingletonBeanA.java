package io.micronaut.inject.dependent;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

import static io.micronaut.inject.dependent.TestData.DESTRUCTION_ORDER;

@Singleton
public class SingletonBeanA {
    @Inject public BeanB beanBField;
    public final BeanB beanBConstructor;
    public BeanB beanBMethod;
    public boolean destroyed;
    @Inject public List<InterfaceA> collection;

    public SingletonBeanA(BeanB beanBConstructor) {
        this.beanBConstructor = beanBConstructor;
    }

    @Inject
    public void setBeanBMethod(BeanB beanBMethod) {
        this.beanBMethod = beanBMethod;
    }

    @PreDestroy
    void stop() {
        DESTRUCTION_ORDER.add(SingletonBeanA.class.getSimpleName());
        this.destroyed = true;
    }
}
