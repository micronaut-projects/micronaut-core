/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.aop.around

// tag::imports[]
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import java.util.Objects
import jakarta.inject.Singleton
// end::imports[]

// tag::interceptor[]
@Singleton
@InterceptorBean(NotNull::class) // <1>
class NotNullInterceptor : MethodInterceptor<Any, Any> { // <2>
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        val nullParam = context.parameters
                .entries
                .stream()
                .filter { entry ->
                    val argumentValue = entry.value
                    Objects.isNull(argumentValue.value)
                }
                .findFirst() // <3>
        return if (nullParam.isPresent) {
            throw IllegalArgumentException("Null parameter [${nullParam.get().key}] not allowed") // <4>
        } else {
            context.proceed() // <5>
        }
    }
}
// end::interceptor[]
