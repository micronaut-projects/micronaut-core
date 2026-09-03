/*
 * Copyright 2017-2026 original authors
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

// tag::imports[]
import io.micronaut.aop.InterceptedMethod
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.ConversionService
import jakarta.inject.Singleton

import java.util.concurrent.CompletionStage
import java.util.function.Function
// end::imports[]

// tag::interceptor[]
@Requires(property = "spec.name", value = "TxSpec")
@Singleton
@InterceptorBean(Array(classOf[Tx]))
class TxInterceptor(txManager: TxManager, conversionService: ConversionService)
    extends MethodInterceptor[AnyRef, AnyRef]:

  override def intercept(context: MethodInvocationContext[AnyRef, AnyRef]): AnyRef =
    val interceptedMethod = InterceptedMethod.of(context, conversionService)
    try
      if interceptedMethod.resultType() == InterceptedMethod.ResultType.COMPLETION_STAGE then
        interceptedMethod.handleResult(
          txManager.inTransaction[AnyRef](new Function[String, CompletionStage[AnyRef]]:
            override def apply(tx: String): CompletionStage[AnyRef] =
              interceptedMethod.interceptResultAsCompletionStage().asInstanceOf[CompletionStage[AnyRef]]
          )
        ).asInstanceOf[AnyRef]
      else
        throw IllegalStateException("This interceptor supports only COMPLETION_STAGE!")
    catch
      case e: Exception =>
        interceptedMethod.handleException[RuntimeException](e).asInstanceOf[AnyRef]
// end::interceptor[]
