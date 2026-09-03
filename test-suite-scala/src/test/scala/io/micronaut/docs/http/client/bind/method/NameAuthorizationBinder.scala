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
package io.micronaut.docs.http.client.bind.method

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.bind.AnnotatedClientRequestBinder
import io.micronaut.http.client.bind.ClientRequestUriContext
import jakarta.inject.Singleton

//tag::clazz[]

@Singleton // <1>
class NameAuthorizationBinder extends AnnotatedClientRequestBinder[NameAuthorization]: // <2>
  override def getAnnotationType: Class[NameAuthorization] =
    classOf[NameAuthorization]

  override def bind( // <3>
      context: MethodInvocationContext[Object, Object],
      uriContext: ClientRequestUriContext,
      request: MutableHttpRequest[?]
  ): Unit =
    val name = context.stringValue(classOf[NameAuthorization], "name")
    if name.isPresent then
      uriContext.addQueryParameter("name", name.get())
    else
      context.getValue(classOf[NameAuthorization])
        .ifPresent(value => uriContext.addQueryParameter("name", String.valueOf(value)))
//end::clazz[]
