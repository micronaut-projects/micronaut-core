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
package io.micronaut.kotlin.processing.aop.introduction.delegation

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import jakarta.inject.Singleton

@Singleton
class DelegatingInterceptor : MethodInterceptor<Delegating, Any> {

    override fun intercept(context: MethodInvocationContext<Delegating, Any>): Any? {
        val executableMethod = context.executableMethod
        val parameterValues = context.parameterValues
        return if (executableMethod.name == "test2") {
            val instance: DelegatingIntroduced = object : DelegatingIntroduced {
                override fun test2(): String {
                    return "good"
                }

                override fun test(): String {
                    return "good"
                }
            }
            executableMethod.invoke(instance, *parameterValues)
        } else {
            executableMethod.invoke(DelegatingImpl(), *parameterValues)
        }
    }
}
