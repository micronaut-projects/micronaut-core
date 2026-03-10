package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.type.MutableArgumentValue
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton

@Singleton
class StubIntroducer : MethodInterceptor<Any, Any> {

    override fun getOrder(): Int {
        return POSITION
    }

    companion object {
        const val POSITION = 0
    }

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        val value = context.stringValue(// <3>
            Stub::class.java
        ).orElse(null)
        if (StringUtils.isNotEmpty(value)) {
            return value
        }
        val iterator: Iterator<MutableArgumentValue<*>> = context.parameters.values.iterator()
        return if (iterator.hasNext()) iterator.next().value?.toString() else null
    }
}
