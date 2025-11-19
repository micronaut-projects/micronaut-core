package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import jakarta.inject.Singleton
import java.lang.reflect.Method

@Singleton
class MyRepoIntroducer : MethodInterceptor<Any, Any> {

    var executableMethods = mutableListOf<Method>()

    override fun getOrder(): Int {
        return 0
    }

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        executableMethods.add(context.executableMethod.targetMethod)
        return null
    }
}
