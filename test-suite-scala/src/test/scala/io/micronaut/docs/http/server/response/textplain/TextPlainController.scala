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
package io.micronaut.docs.http.server.response.textplain

import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.math.BigDecimal
import java.util.Calendar

@Requires(property = "spec.name", value = "TextPlainControllerTest")
// tag::classopening[]
@Controller("/txt")
class TextPlainController:
// end::classopening[]

  @Get("/boolean")
  @Produces(Array(MediaType.TEXT_PLAIN)) // <1>
  def bool(): String =
    java.lang.Boolean.TRUE.toString // <2>

  @Get("/boolean/mono")
  @Produces(Array(MediaType.TEXT_PLAIN)) // <1>
  @SingleResult
  def monoBool(): Publisher[String] =
    Mono.just(java.lang.Boolean.TRUE.toString) // <2>

  @Get("/boolean/flux")
  @Produces(Array(MediaType.TEXT_PLAIN))
  @SingleResult
  def fluxBool(): Publisher[String] =
    Flux.just(java.lang.Boolean.TRUE.toString)

  @Get("/bigdecimal")
  @Produces(Array(MediaType.TEXT_PLAIN)) // <1>
  def bigDecimal(): String =
    BigDecimal.valueOf(Long.MaxValue).toString // <2>

  // tag::method[]
  @Get("/date")
  @Produces(Array(MediaType.TEXT_PLAIN)) // <1>
  def date(): String =
    Calendar.Builder().setDate(2023, 7, 4).build().toString // <2>
  // end::method[]

  @Get("/person")
  @Produces(Array(MediaType.TEXT_PLAIN)) // <1>
  def person(): String =
    Person("Dean Wette", 65).toString // <2>

// tag::classclosing[]
// end::classclosing[]

case class Person(name: String, age: Int)
