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
package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
import java.util.HashSet

/**
 * @author graemerocher
 * @since 1.0
 */
@Singleton
class ListenerAdviceInterceptor : MethodInterceptor<Any, Any> {

    private val recievedMessages: MutableSet<Any> = HashSet()

    override fun getOrder(): Int {
        return StubIntroducer.POSITION - 10
    }

    fun getRecievedMessages(): Set<Any> {
        return recievedMessages
    }

    @Nullable
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        return if (context.methodName == "onApplicationEvent") {
            val v = context.parameterValues[0]
            recievedMessages.add(v)
            null
        } else {
            context.proceed()
        }
    }
}
