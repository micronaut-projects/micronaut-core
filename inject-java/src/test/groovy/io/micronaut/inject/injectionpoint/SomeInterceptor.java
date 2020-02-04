package io.micronaut.inject.injectionpoint;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.InjectionPoint;

@Prototype
public class SomeInterceptor implements MethodInterceptor<Object, Object> {

    private final String name;

    public SomeInterceptor(InjectionPoint<SomeInterceptor> injectionPoint) {
        this.name = injectionPoint
                .getAnnotationMetadata()
                .stringValue(SomeAnn.class)
                .orElse("no value");
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return name;
    }
}
