package io.micronaut.aop.adapter.priv3.test2;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

/**
 * Order 2 so that Interceptor1J (order 1) executes first and this runs after,
 * resulting in "beaninterceptor2interceptor1".
 */
@Requires(property = "spec.name", value = "InterceptorPrivate3SpecJ")
@Prototype
public class TestInterceptor2J implements MethodInterceptor<Object, Object> {

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed() + "interceptor2";
    }

    @Override
    public int getOrder() {
        return 2;
    }
}
