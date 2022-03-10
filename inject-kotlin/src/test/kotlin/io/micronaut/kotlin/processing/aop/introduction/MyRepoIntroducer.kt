package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
import java.lang.reflect.Method
import java.util.ArrayList

@Singleton
class MyRepoIntroducer : MethodInterceptor<Any, Any> {

    var executableMethods = mutableListOf<Method>()

    override fun getOrder(): Int {
        return 0
    }

    @Nullable
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        executableMethods.add(context.executableMethod.targetMethod)
        return null
    }
}
