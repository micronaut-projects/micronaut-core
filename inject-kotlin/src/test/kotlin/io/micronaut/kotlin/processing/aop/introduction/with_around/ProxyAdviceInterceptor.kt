/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.kotlin.processing.aop.introduction.with_around

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.BeanContext
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
import java.lang.RuntimeException

@Singleton
class ProxyAdviceInterceptor(private val beanContext: BeanContext) : MethodInterceptor<Any, Any> {

    @Nullable
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        if (context.methodName.equals("getId", ignoreCase = true)) {
            // Test invocation delegation
            return if (context.target is MyBean5) {
                val delegate = MyBean5()
                delegate.setId(1L)
                context.executableMethod.invoke(delegate, *context.parameterValues)
            } else if (context.target is MyBean6) {
                try {
                    val proxyTargetMethod = beanContext.getProxyTargetMethod<MyBean6, Any>(
                        MyBean6::class.java, context.methodName, *context.argumentTypes
                    )
                    val delegate = MyBean6()
                    delegate.setId(1L)
                    proxyTargetMethod.invoke(delegate, *context.parameterValues)
                } catch (e: NoSuchMethodException) {
                    throw RuntimeException(e)
                }
            } else {
                1L
            }
        }
        return if (context.methodName.equals("isProxy", ignoreCase = true)) {
            true
        } else context.proceed()
    }
}
