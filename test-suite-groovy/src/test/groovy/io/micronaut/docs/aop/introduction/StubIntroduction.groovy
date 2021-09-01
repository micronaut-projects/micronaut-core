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
package io.micronaut.docs.aop.introduction

// tag::imports[]
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.aop.InterceptorBean
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
@InterceptorBean(Stub) // <1>
class StubIntroduction implements MethodInterceptor<Object,Object> { // <2>

    @Nullable
    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        context.getValue( // <3>
                Stub,
                context.returnType.type
        ).orElse(null) // <4>
    }
}
// end::class[]
