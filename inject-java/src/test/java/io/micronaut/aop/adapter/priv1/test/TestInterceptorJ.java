package io.micronaut.aop.adapter.priv1.test;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

/**
 * Dedicated interceptor for the InterceptorPrivate1SpecJ variant to avoid FQCN clashes
 * with the original Groovy-based TestInterceptor.
 */
@Requires(property = "spec.name", value = "InterceptorPrivate1SpecJ")
@Prototype
public class TestInterceptorJ implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return "interceptor";
    }
}
