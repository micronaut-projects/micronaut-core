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
package io.micronaut.docs.server.body

// tag::imports[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import jakarta.validation.constraints.Size
// end::imports[]
// tag::importsreactive[]
import io.micronaut.core.async.annotation.SingleResult
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
// end::importsreactive[]

// tag::class[]
@Controller("/receive")
class MessageController:
// end::class[]

  // tag::echo[]
  @Post(value = "/echo", consumes = Array(MediaType.TEXT_PLAIN)) // <1>
  def echo(@Size(max = 1024) @Body text: String): String = // <2>
    text // <3>
  // end::echo[]

  // tag::echoReactive[]
  @Post(value = "/echo-publisher", consumes = Array(MediaType.TEXT_PLAIN)) // <1>
  @SingleResult
  def echoFlow(@Body text: Publisher[String]): Publisher[HttpResponse[String]] = // <2>
    Flux.from(text)
      .collect(() => StringBuffer(), (buffer, value) => buffer.append(value)) // <3>
      .map(buffer => HttpResponse.ok(buffer.toString))
  // end::echoReactive[]

// tag::endclass[]
// end::endclass[]
