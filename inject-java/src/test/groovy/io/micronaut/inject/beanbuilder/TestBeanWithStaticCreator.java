package io.micronaut.inject.beanbuilder;

public class TestBeanWithStaticCreator {
    private TestBeanWithStaticCreator() {}
    public static TestBeanWithStaticCreator create() {
        return new TestBeanWithStaticCreator();
    }
}
