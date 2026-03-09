package io.micronaut.aop.adapter.priv2.test;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

/**
 * JUnit variant bound to TestAnnotationJ and gated by spec.name.
 */
@Requires(property = "spec.name", value = "InterceptorPrivate2SpecJ")
@InterceptorBean(TestAnnotationJ.class)
@Prototype
public class TestInterceptorJ implements MethodInterceptor<Object, Object> {

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return "interceptor";
    }
}
