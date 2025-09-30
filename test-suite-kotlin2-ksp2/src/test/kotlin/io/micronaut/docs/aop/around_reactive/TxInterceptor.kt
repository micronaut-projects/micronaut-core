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
package io.micronaut.docs.aop.around_reactive

import io.micronaut.aop.InterceptedMethod
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.convert.ConversionService
import jakarta.inject.Singleton

@Singleton
@InterceptorBean(Tx::class)
class TxInterceptor internal constructor(
    private val txManager: TxManager, private val conversionService: ConversionService
) : MethodInterceptor<Any?, Any?> {

    override fun intercept(context: MethodInvocationContext<Any?, Any?>): Any? {
        val interceptedMethod = InterceptedMethod.of(context, conversionService)
        return try {
            if (interceptedMethod.resultType() == InterceptedMethod.ResultType.COMPLETION_STAGE) {
                return interceptedMethod.handleResult(
                    txManager.inTransaction() { tx: String? -> interceptedMethod.interceptResultAsCompletionStage() }
                )
            }
            throw IllegalStateException("This interceptor supports only COMPLETION_STAGE!")
        } catch (e: Exception) {
            interceptedMethod.handleException<RuntimeException>(e)
        }
    }

}
