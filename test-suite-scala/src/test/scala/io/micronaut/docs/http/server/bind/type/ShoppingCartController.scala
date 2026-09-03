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

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

import scala.jdk.CollectionConverters.*

@Requires(property = "spec.name", value = "ShoppingCartControllerTest")
@Controller("/customBinding")
class ShoppingCartController:

  // tag::method[]
  @Get("/typed")
  def loadCart(shoppingCart: ShoppingCart): HttpResponse[?] = //<1>
    val responseMap = Map[String, Object](
      "sessionId" -> shoppingCart.sessionId,
      "total" -> shoppingCart.total
    ).asJava

    HttpResponse.ok(responseMap)
  // end::method[]
