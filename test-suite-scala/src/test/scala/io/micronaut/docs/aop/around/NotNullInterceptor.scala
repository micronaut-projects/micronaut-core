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
package io.micronaut.docs.aop.around

// tag::imports[]
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Requires

import jakarta.inject.Singleton
import scala.jdk.CollectionConverters.*
// end::imports[]

// tag::interceptor[]
@Requires(property = "spec.name", value = "AroundSpec")
@Singleton
@InterceptorBean(Array(classOf[NotNull])) // <1>
class NotNullInterceptor extends MethodInterceptor[Object, Object]: // <2>
  override def intercept(context: MethodInvocationContext[Object, Object]): Object =
    val nullParam = context.getParameters
      .entrySet()
      .asScala
      .find(entry => entry.getValue.getValue == null) // <3>
    nullParam match
      case Some(entry) =>
        throw IllegalArgumentException(s"Null parameter [${entry.getKey}] not allowed") // <4>
      case None =>
        context.proceed() // <5>
// end::interceptor[]
