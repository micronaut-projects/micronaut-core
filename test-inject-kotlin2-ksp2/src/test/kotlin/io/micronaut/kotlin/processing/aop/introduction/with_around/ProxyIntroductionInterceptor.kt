package io.micronaut.kotlin.processing.aop.introduction.with_around

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton

@Singleton
class ProxyIntroductionInterceptor : MethodInterceptor<Any, Any> {

    @Nullable
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        // Only intercept CustomProxy
        if (context.methodName.equals("isProxy", ignoreCase = true)) {
            // test introduced interface delegation
            val customProxy = object : CustomProxy {
                override fun isProxy(): Boolean {
                    return true
                }
            }
            return context.executableMethod.invoke(customProxy, *context.parameterValues)
        }
        return context.proceed()
    }
}
