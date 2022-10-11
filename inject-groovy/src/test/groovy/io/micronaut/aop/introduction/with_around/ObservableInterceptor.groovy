package io.micronaut.aop.introduction.with_around

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext

import javax.inject.Singleton

@Singleton
class ObservableInterceptor implements MethodInterceptor<Object, Object> {

    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        "World"
    }
}
