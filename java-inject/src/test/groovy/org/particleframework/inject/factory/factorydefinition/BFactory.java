package org.particleframework.inject.factory.factorydefinition;

import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

@Factory
public class BFactory {
    String name = "fromFactory";
    boolean postConstructCalled = false;
    boolean getCalled = false;
    @Inject
    private A fieldA;
    @Inject protected A anotherField;
    @Inject A a;
    private A methodInjected;

    @Inject
    private Object injectMe(A a) {
        methodInjected = a;
        return methodInjected;
    }

    public A getFieldA() {
        return fieldA;
    }

    public A getAnotherField() {
        return anotherField;
    }

    public A getMethodInjected() {
        return methodInjected;
    }

    @PostConstruct
    public void init() {
        assertState();
        postConstructCalled = true;
        name = name.toUpperCase();
    }

    @Bean
    @Singleton
    public B get() {
        assert postConstructCalled : "post construct should have been called";
        assertState();

        getCalled = true;
        B b = new B();
        b.setName(name);
        return b;
    }

    @Bean
    public C buildC(B b) {
        C c = new C();
        c.setB(b);
        return c;
    }

    private void assertState() {
        assert fieldA != null: "private fields should have been injected first";
        assert anotherField != null: "protected fields should have been injected field";
        assert a != null: "public properties should have been injected first";
        assert methodInjected != null: "methods should have been injected first";
    }
}
