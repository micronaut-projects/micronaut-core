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
package io.micronaut.docs.http.server.cors

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.cors.CrossOrigin
// end::imports[]

@Requires(property = "spec.name", value = "CorsControllerSpec")
// tag::controller[]
@Controller("/hello")
class CorsController:
  @CrossOrigin(Array("https://myui.com")) // <1>
  @Get(produces = Array(MediaType.TEXT_PLAIN)) // <2>
  def cors(): String =
    "Welcome to the worlds of CORS"

  @Produces(Array(MediaType.TEXT_PLAIN))
  @Get("/nocors") // <3>
  def nocorstoday(): String =
    "No more CORS for you"
// end::controller[]
