package io.micronaut.kotlin.processing.aop.introduction.with_around

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import jakarta.inject.Singleton

@Singleton
class ProxyAroundInterceptor : MethodInterceptor<Any, Any> {

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        // Intercept everything other when CustomProxy
        return if (context.methodName == "getId") {
            1L
        } else context.proceed()
    }
}
