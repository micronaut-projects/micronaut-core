package io.micronaut.inject.beanbuilder;

public class TestBeanWithStaticCreator implements BeanWithStaticCreator {
    private TestBeanWithStaticCreator() {}
    public static TestBeanWithStaticCreator create() {
        return new TestBeanWithStaticCreator();
    }
}
