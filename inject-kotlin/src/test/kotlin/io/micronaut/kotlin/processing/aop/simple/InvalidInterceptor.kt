package io.micronaut.kotlin.processing.aop.simple

import io.micronaut.aop.Interceptor
import io.micronaut.aop.InvocationContext
import io.micronaut.core.type.Argument
import io.micronaut.core.type.MutableArgumentValue
import jakarta.inject.Singleton

@Singleton
class InvalidInterceptor : Interceptor<Any?, Any?> {

    override fun intercept(context: InvocationContext<Any?, Any?>): Any? {
        context.parameters["test"] = MutableArgumentValue.create(
            Argument.STRING,
            "value"
        )
        return context.proceed()
    }
}
