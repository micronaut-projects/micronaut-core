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
