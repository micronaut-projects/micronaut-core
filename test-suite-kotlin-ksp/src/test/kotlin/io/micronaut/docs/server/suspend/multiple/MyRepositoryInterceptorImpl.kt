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
import java.io.IOException
import java.util.concurrent.CompletableFuture

@InterceptorBean(MyRepository::class)
@Singleton
class MyRepositoryInterceptorImpl : MethodInterceptor<Any, Any> {
    override fun intercept(context: MethodInvocationContext<Any, Any>?): Any? {
        val interceptedMethod = InterceptedMethod.of(context)
        return try {
            if (interceptedMethod.resultType() == InterceptedMethod.ResultType.COMPLETION_STAGE) {
                MyService.events.add("repository-" + context!!.methodName)
                val cf: CompletableFuture<String> = CompletableFuture.supplyAsync{
                    Thread.sleep(1000)
                    context!!.methodName
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