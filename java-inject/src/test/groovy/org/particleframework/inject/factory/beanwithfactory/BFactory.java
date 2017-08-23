package org.particleframework.inject.factory.beanwithfactory;

import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

@Factory
public class BFactory  {
    String name = "original";
    boolean postConstructCalled = false;
    boolean getCalled = false;

    @Inject
    private A fieldA;
    @Inject
    protected A anotherField;
    @Inject
    A a;

    private A methodInjected;

    @Inject
    private Object injectMe(A a) {
        methodInjected = a;
        return methodInjected;
    }

    A getFieldA() {
        return fieldA;
    }

    A getAnotherField() {
        return anotherField;
    }

    A getMethodInjected() {
        return methodInjected;
    }

    @PostConstruct
    void init() {
        postConstructCalled = true;
        name = name.toUpperCase();
    }

    @Bean
    public B get() {
        getCalled = true;
        B b = new B();
        b.setName(name);
        return b;
    }
}
