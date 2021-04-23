package io.micronaut.aop.constructor;

import io.micronaut.aop.ConstructorInterceptor;
import io.micronaut.aop.ConstructorInvocationContext;
import io.micronaut.aop.InterceptorBean;

@InterceptorBean(TestConstructorAnn.class)
public class TestConstructorInterceptor implements ConstructorInterceptor<Object> {
    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        return context.proceed();
    }
}
