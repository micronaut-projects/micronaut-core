package io.micronaut.aop.introduction.with_around

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext

import javax.inject.Singleton

@Singleton
class ProxyIntroductionInterceptor implements MethodInterceptor<Object, Object> {

    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        // Only intercept CustomProxy
        if (context.getMethodName().equalsIgnoreCase("isProxy")) {
            return true
        }
        return context.proceed()
    }
}
