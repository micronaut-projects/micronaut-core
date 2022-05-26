package io.micronaut.inject.beanbuilder;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class TestBeanProducer {
    @TestProduces
    public BeanA beanA = new BeanA();

    @TestProduces
    public BeanB beanB() {
        return new BeanB();
    }

    public static class BeanA {

    }

    public static class BeanB {

    }
}
