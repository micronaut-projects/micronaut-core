package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.type.MutableArgumentValue
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton

@Singleton
class StubIntroducer : MethodInterceptor<Any?, Any?> {

    override fun getOrder(): Int {
        return POSITION
    }

    companion object {
        const val POSITION = 0
    }

    override fun intercept(context: MethodInvocationContext<Any?, Any?>): Any? {
        return context.stringValue(// <3>
            Stub::class.java
        ).filter { StringUtils.isNotEmpty(it) }.orElseGet {
            val iterator: Iterator<MutableArgumentValue<*>> = context.parameters.values.iterator()
            if (iterator.hasNext()) iterator.next().value?.toString() else null
        }
    }
}
