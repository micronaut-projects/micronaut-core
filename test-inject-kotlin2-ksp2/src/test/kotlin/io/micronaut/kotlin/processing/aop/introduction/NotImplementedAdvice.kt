package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import jakarta.inject.Singleton

@Singleton
class NotImplementedAdvice : MethodInterceptor<Any, Any> {
    var invoked = false

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        invoked = true
        return context.proceed()
    }
}
