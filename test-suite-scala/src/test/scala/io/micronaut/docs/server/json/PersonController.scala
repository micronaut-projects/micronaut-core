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
package io.micronaut.docs.server.json

import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.micronaut.json.JsonSyntaxException
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

import java.util.Collection
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Requires(property = "spec.name", value = "PersonControllerSpec")
// tag::class[]
@Controller("/people")
class PersonController:

  private val inMemoryDatastore = new ConcurrentHashMap[String, Person]()
// end::class[]

  @Get
  def index(): Collection[Person] =
    inMemoryDatastore.values()

  @Get("/{name}")
  @SingleResult
  def get(name: String): Publisher[Person] =
    if inMemoryDatastore.containsKey(name) then
      Mono.just(inMemoryDatastore.get(name))
    else
      Mono.empty[Person]()

  // tag::single[]
  @Post("/saveReactive")
  @SingleResult
  def save(@Body person: Publisher[Person]): Publisher[HttpResponse[Person]] = // <1>
    Mono.from(person).map { p =>
      inMemoryDatastore.put(p.firstName, p) // <2>
      HttpResponse.created(p) // <3>
    }
  // end::single[]

  // tag::args[]
  @Post("/saveWithArgs")
  def save(firstName: String, lastName: String, age: Optional[Integer]): HttpResponse[Person] =
    val p = new Person(firstName, lastName)
    age.ifPresent((value: Integer) => p.age = value.intValue())
    inMemoryDatastore.put(p.firstName, p)
    HttpResponse.created(p)
  // end::args[]

  // tag::future[]
  @Post("/saveFuture")
  def save(@Body person: CompletableFuture[Person]): CompletableFuture[HttpResponse[Person]] =
    person.thenApply { p =>
      inMemoryDatastore.put(p.firstName, p)
      HttpResponse.created(p)
    }
  // end::future[]

  // tag::regular[]
  @Post
  def save(@Body person: Person): HttpResponse[Person] =
    inMemoryDatastore.put(person.firstName, person)
    HttpResponse.created(person)
  // end::regular[]

  // tag::localError[]
  @Error
  def jsonError(request: HttpRequest[?], e: JsonSyntaxException): HttpResponse[JsonError] = // <1>
    val error = new JsonError("Invalid JSON: " + e.getMessage) // <2>
      .link(Link.SELF, Link.of(request.getUri))

    HttpResponse.status[JsonError](HttpStatus.BAD_REQUEST, "Fix Your JSON")
      .body(error) // <3>
  // end::localError[]

  @Get("/error")
  def throwError(): String =
    throw new RuntimeException("Something went wrong")

  // tag::globalError[]
  @Error(global = true) // <1>
  def error(request: HttpRequest[?], e: Throwable): HttpResponse[JsonError] =
    val error = new JsonError("Bad Things Happened: " + e.getMessage) // <2>
      .link(Link.SELF, Link.of(request.getUri))

    HttpResponse.serverError[JsonError]()
      .body(error) // <3>
  // end::globalError[]

  // tag::statusError[]
  @Error(status = HttpStatus.NOT_FOUND)
  def notFound(request: HttpRequest[?]): HttpResponse[JsonError] = // <1>
    val error = new JsonError("Person Not Found") // <2>
      .link(Link.SELF, Link.of(request.getUri))

    HttpResponse.notFound[JsonError]()
      .body(error) // <3>
  // end::statusError[]

// tag::endclass[]
// end::endclass[]
