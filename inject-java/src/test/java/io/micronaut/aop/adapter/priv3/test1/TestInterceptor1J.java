package io.micronaut.aop.adapter.priv3.test1;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

/**
 * Order 1 so it appends after TestInterceptor2J to yield ...interceptor2interceptor1
 */
@Requires(property = "spec.name", value = "InterceptorPrivate3SpecJ")
@InterceptorBean(TestAnnotation1J.class)
@Prototype
public class TestInterceptor1J implements MethodInterceptor<Object, Object> {

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed() + "interceptor1";
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
