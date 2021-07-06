package io.micronaut.aop.introduction.with_around

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext

import javax.inject.Singleton

@Singleton
class ProxyAroundInterceptor implements MethodInterceptor<Object, Object> {

    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        // Intercept everything other when CustomProxy
        if (context.getMethodName().equalsIgnoreCase("getId")) {
            return 1L
        }
        return context.proceed()
    }
}
