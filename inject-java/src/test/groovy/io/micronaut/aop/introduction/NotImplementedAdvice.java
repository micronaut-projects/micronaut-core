package io.micronaut.aop.introduction;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;

import javax.inject.Singleton;

@Singleton
public class NotImplementedAdvice implements MethodInterceptor<Object, Object> {
    public static boolean invoked = false;
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        invoked = true;
        return context.proceed();
    }
}
