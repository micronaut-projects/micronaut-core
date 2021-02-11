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
// end::imports[]

// tag::interceptor[]
@Singleton
class NotNullInterceptor implements MethodInterceptor<Object, Object> { // <1>
    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        Optional<Map.Entry<String, MutableArgumentValue<?>>> nullParam = context.parameters
            .entrySet()
            .stream()
            .filter({entry ->
                MutableArgumentValue<?> argumentValue = entry.value
                return Objects.isNull(argumentValue.value)
            })
            .findFirst() // <2>
        if (nullParam.present) {
            throw new IllegalArgumentException("Null parameter [${nullParam.get().key}] not allowed") // <3>
        }
        return context.proceed() // <4>
    }
}
// end::interceptor[]
