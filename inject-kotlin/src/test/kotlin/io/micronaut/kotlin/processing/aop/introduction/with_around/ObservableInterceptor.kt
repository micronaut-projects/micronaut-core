package io.micronaut.kotlin.processing.aop.introduction.with_around

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import jakarta.inject.Singleton

@Singleton
class ObservableInterceptor : MethodInterceptor<Any?, Any?> {

    override fun intercept(context: MethodInvocationContext<Any?, Any?>?): Any {
        return "World"
    }
}
