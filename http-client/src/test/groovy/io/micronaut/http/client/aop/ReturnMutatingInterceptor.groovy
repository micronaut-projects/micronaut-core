package io.micronaut.http.client.aop

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext

import javax.inject.Singleton

@Singleton
class ReturnMutatingInterceptor implements MethodInterceptor<Object, String> {

    @Override
    String intercept(MethodInvocationContext<Object, String> context) {
        return context.proceed() + " mutated"
    }
}