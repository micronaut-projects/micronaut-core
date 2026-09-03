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
package io.micronaut.docs.http.server.bind.`type`

// tag::class[]
import io.micronaut.core.bind.ArgumentBinder.BindingResult
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.`type`.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import io.micronaut.http.cookie.Cookie
import io.micronaut.jackson.serialize.JacksonObjectSerializer

import jakarta.inject.Singleton
import java.util.Optional

@Singleton
class ShoppingCartRequestArgumentBinder(
    objectSerializer: JacksonObjectSerializer
) extends TypedRequestArgumentBinder[ShoppingCart]:

  override def bind(
      context: ArgumentConversionContext[ShoppingCart],
      source: HttpRequest[?]
  ): BindingResult[ShoppingCart] = //<1>

    val cookie: Cookie = source.getCookies.get("shoppingCart")
    if cookie == null then
      () => Optional.empty[ShoppingCart]()
    else
      () => objectSerializer.deserialize( //<2>
        cookie.getValue.getBytes,
        classOf[ShoppingCart]
      )

  override def argumentType(): Argument[ShoppingCart] =
    Argument.of(classOf[ShoppingCart]) //<3>
// end::class[]
