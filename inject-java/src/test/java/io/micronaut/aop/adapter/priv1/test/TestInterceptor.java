package io.micronaut.aop.adapter.priv1.test;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;

@Prototype
class TestInterceptorDisabled implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return "interceptor";
    }
}
