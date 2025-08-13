package io.micronaut.aop.adapter.priv3.test1;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;

@Order(1)
@Requires(property = "spec.name", value = "InterceptorPrivate3Spec")
@InterceptorBean(TestAnnotation1.class)
@Prototype
class TestInterceptor1 implements MethodInterceptor<Object, Object> {
	@Override
	public Object intercept(MethodInvocationContext<Object, Object> context) {
		return context.proceed() + "interceptor1";
	}
}
