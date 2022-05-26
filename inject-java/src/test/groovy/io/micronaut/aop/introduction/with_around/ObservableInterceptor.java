package io.micronaut.aop.introduction.with_around;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

@Singleton
public class ObservableInterceptor implements MethodInterceptor<Object, Object> {
    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return "World";
    }
}
