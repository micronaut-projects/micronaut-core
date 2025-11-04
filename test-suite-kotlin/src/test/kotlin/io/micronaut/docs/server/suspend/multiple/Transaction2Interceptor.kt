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
package io.micronaut.docs.server.suspend.multiple

import io.micronaut.aop.InterceptedMethod
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import jakarta.inject.Singleton
import java.util.concurrent.CompletableFuture

@InterceptorBean(Transaction2::class)
@Singleton
class Transaction2Interceptor : MethodInterceptor<Any, Any> {
    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        val interceptedMethod = InterceptedMethod.of(context)
        return try {
            return if (interceptedMethod.resultType() == InterceptedMethod.ResultType.COMPLETION_STAGE) {
                MyService.events.add("intercept2-start")
                val completionStage = interceptedMethod.interceptResultAsCompletionStage()
                val cf = CompletableFuture<Any>()
                completionStage.whenComplete { value, throwable ->
                    MyService.events.add("intercept2-end")
                    if (throwable == null) {
                        cf.complete(value)
                    } else {
                        cf.completeExceptionally(throwable)
                    }
                }
                interceptedMethod.handleResult(cf)
            } else {
                throw IllegalStateException()
            }
        } catch (e: Exception) {
            interceptedMethod.handleException<Exception>(e)
        }
    }
}