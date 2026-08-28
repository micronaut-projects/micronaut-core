package io.micronaut.kotlin.processing.aop.proxytarget

import io.micronaut.aop.Interceptor
import io.micronaut.aop.InvocationContext
import io.micronaut.core.type.MutableArgumentValue
import jakarta.inject.Singleton

@Singleton
class ArgMutatingInterceptor : Interceptor<Any, Any> {

    override fun intercept(context: InvocationContext<Any, Any>): Any? {
        val m = context.stringValue(
            Mutating::class.java
        )
        if (m.isPresent) {
            val arg = context.parameters[m.get()] as MutableArgumentValue<Any>?
            if (arg != null) {
                val value = arg.value
                if (value is Number) {
                    arg.setValue((value.toInt() * 2))
                } else {
                    arg.setValue("changed")
                }
            }
        }
        return context.proceed()
    }
}
