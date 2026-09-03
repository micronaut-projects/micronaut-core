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
package io.micronaut.docs.http.server.bind.annotation

// tag::class[]
import io.micronaut.core.bind.ArgumentBinder.BindingResult
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.`type`.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder
import io.micronaut.http.cookie.Cookie
import io.micronaut.jackson.serialize.JacksonObjectSerializer

import jakarta.inject.Singleton
import java.util.Map

@Singleton
class ShoppingCartRequestArgumentBinder(
    conversionService: ConversionService,
    objectSerializer: JacksonObjectSerializer
) extends AnnotatedRequestArgumentBinder[ShoppingCart, Object]: //<1>

  override def getAnnotationType: Class[ShoppingCart] =
    classOf[ShoppingCart]

  override def bind(
      context: ArgumentConversionContext[Object],
      source: HttpRequest[?]
  ): BindingResult[Object] = //<2>

    val parameterName = context.getAnnotationMetadata
      .stringValue(classOf[ShoppingCart])
      .orElse(context.getArgument.getName)

    val cookie: Cookie = source.getCookies.get("shoppingCart")
    if cookie == null then
      BindingResult.EMPTY.asInstanceOf[BindingResult[Object]]
    else
      val cookieValue = objectSerializer.deserialize(
        cookie.getValue.getBytes,
        Argument.mapOf(classOf[String], classOf[Object])
      )

      () => cookieValue.flatMap((map: Map[String, Object]) =>
        conversionService.convert(map.get(parameterName), context)
      )
// end::class[]
