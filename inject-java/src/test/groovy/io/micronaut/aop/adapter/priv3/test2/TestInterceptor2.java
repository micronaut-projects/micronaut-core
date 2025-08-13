package io.micronaut.aop.adapter.priv3.test2;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;

@Order(2)
@Requires(property = "spec.name", value = "InterceptorPrivate3Spec")
@Prototype
class TestInterceptor2 implements MethodInterceptor<Object, Object> {
	@Override
	public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed() + "interceptor2";
	}
}
