package io.micronaut.docs.aop.around

// tag::imports[]

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.type.MutableArgumentValue

import javax.inject.Singleton
import java.util.Objects
import java.util.Optional

// end::imports[]

// tag::interceptor[]
@Singleton
class NotNullInterceptor : MethodInterceptor<Any, Any> { // <1>
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any {
        val nullParam = context.parameters
                .entries
                .stream()
                .filter { entry ->
                    val argumentValue = entry.value
                    Objects.isNull(argumentValue.value)
                }
                .findFirst() // <2>
        return if (nullParam.isPresent) {
            throw IllegalArgumentException("Null parameter [" + nullParam.get().key + "] not allowed") // <3>
        } else {
            context.proceed() // <4>
        }
    }
}
// end::interceptor[]
