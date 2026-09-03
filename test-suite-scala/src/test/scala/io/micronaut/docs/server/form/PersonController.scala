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
package io.micronaut.docs.server.form

import io.micronaut.context.annotation.Requires
import org.jspecify.annotations.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Requires(property = "spec.name", value = "PersonControllerFormTest")
// tag::class[]
@Controller("/people")
class PersonController:

  val inMemoryDatastore = new ConcurrentHashMap[String, Person]()
// end::class[]

  // tag::formbinding[]
  @Consumes(Array(MediaType.APPLICATION_FORM_URLENCODED))
  @Post
  def save(@Body person: Person): HttpResponse[Person] =
    inMemoryDatastore.put(person.firstName, person)
    HttpResponse.created(person)
  // end::formbinding[]

  // tag::formsaveWithArgs[]
  @Consumes(Array(MediaType.APPLICATION_FORM_URLENCODED))
  @Post("/saveWithArgs")
  def save(firstName: String, lastName: String, @Nullable age: Integer | Null): HttpResponse[Person] =
    val p = new Person(firstName, lastName)
    if age != null then
      p.age = age.intValue()
    inMemoryDatastore.put(p.firstName, p)
    HttpResponse.created(p)
  // end::formsaveWithArgs[]

  // tag::formsaveWithArgsOptional[]
  @Consumes(Array(MediaType.APPLICATION_FORM_URLENCODED))
  @Post("/saveWithArgsOptional")
  def save(firstName: String, lastName: String, age: Optional[Integer]): HttpResponse[Person] =
    val p = new Person(firstName, lastName)
    age.ifPresent((value: Integer) => p.age = value.intValue())
    inMemoryDatastore.put(p.firstName, p)
    HttpResponse.created(p)
  // end::formsaveWithArgsOptional[]

// tag::endclass[]
// end::endclass[]
