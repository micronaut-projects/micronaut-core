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
package io.micronaut.docs.server.binding

import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.format.Format
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import org.jspecify.annotations.Nullable

import java.time.ZonedDateTime
import java.util.Arrays
import java.util.List

@Requires(property = "spec.name", value = "BindingControllerSpec")
@Controller("/binding")
class BindingController:

  // tag::cookie1[]
  @Get("/cookieName")
  def cookieName(@CookieValue("myCookie") myCookie: String): String =
    // ...
    myCookie
  // end::cookie1[]

  // tag::cookie2[]
  @Get("/cookieInferred")
  def cookieInferred(@CookieValue myCookie: String): String =
    // ...
    myCookie
  // end::cookie2[]

  // tag::cookieMultiple[]
  @Get("/cookieMultiple")
  def cookieMultiple(
      @CookieValue("myCookieA") myCookieA: String,
      @CookieValue("myCookieB") myCookieB: String
  ): List[String] =
    // ...
    Arrays.asList(myCookieA, myCookieB)
  // end::cookieMultiple[]

  // tag::header1[]
  @Get("/headerName")
  def headerName(@Header("Content-Type") contentType: String): String =
    // ...
    contentType
  // end::header1[]

  // tag::header2[]
  @Get("/headerInferred")
  def headerInferred(@Header contentType: String): String =
    // ...
    contentType
  // end::header2[]

  // tag::header3[]
  @Get("/headerNullable")
  def headerNullable(@Nullable @Header contentType: String | Null): String | Null =
    // ...
    contentType
  // end::header3[]

  // tag::format1[]
  @Get("/date")
  def date(@Header date: ZonedDateTime): String =
    // ...
    date.toString
  // end::format1[]

  // tag::format2[]
  @Get("/dateFormat")
  def dateFormat(@Format("dd/MM/yyyy hh:mm:ss a z") @Header date: ZonedDateTime): String =
    // ...
    date.toString
  // end::format2[]
