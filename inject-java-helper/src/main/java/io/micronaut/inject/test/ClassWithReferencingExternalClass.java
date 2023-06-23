package io.micronaut.inject.test;

import io.micronaut.core.reflect.ClassUtils;
import io.vertx.core.VertxOptions;

// This class is used to test method with classes that are
public class ClassWithReferencingExternalClass {

    static {
        ClassUtils.forName("io.vertx.core.VertxOptions", null).ifPresent(aClass -> {
            throw new IllegalStateException("Class shouldn't be present on the classpath!");
        });
    }

    // Simple method with a class that is not going to be resolved and the compilation time and runtime
    public void method1(VertxOptions conf) {
    }

    public void method2() {
    }

}
