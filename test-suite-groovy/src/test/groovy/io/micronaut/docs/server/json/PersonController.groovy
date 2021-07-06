/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.server.json

import com.fasterxml.jackson.core.JsonParseException
import io.micronaut.context.annotation.Requires
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
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import io.micronaut.core.async.annotation.SingleResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Requires(property = "spec.name", value = "PersonControllerSpec")
// tag::class[]
@Controller("/people")
class PersonController {

    Map<String, Person> inMemoryDatastore = new ConcurrentHashMap<>()
// end::class[]

    @Get
    Collection<Person> index() {
        inMemoryDatastore.values()
    }

    @Get("/{name}")
    @SingleResult
    Publisher<Person> get(String name) {
        Person person = inMemoryDatastore.get(name)
        if (person != null) {
            Mono.just(person)
        } else {
            Mono.empty()
        }
    }

    // tag::single[]
    @Post("/saveReactive")
    @SingleResult
    Publisher<HttpResponse<Person>> save(@Body Publisher<Person> person) { // <1>
        Mono.from(person).map({ p ->
            inMemoryDatastore.put(p.getFirstName(), p) // <2>
            HttpResponse.created(p) // <3>
        })
    }
    // end::single[]

    // tag::args[]
    @Post("/saveWithArgs")
    HttpResponse<Person> save(String firstName, String lastName, Optional<Integer> age) {
        Person p = new Person(firstName, lastName)
        age.ifPresent({ a -> p.setAge(a)})
        inMemoryDatastore.put(p.getFirstName(), p)
        HttpResponse.created(p)
    }
    // end::args[]

    // tag::future[]
    @Post("/saveFuture")
    CompletableFuture<HttpResponse<Person>> save(@Body CompletableFuture<Person> person) {
        person.thenApply({ p ->
            inMemoryDatastore.put(p.getFirstName(), p)
            HttpResponse.created(p)
        })
    }
    // end::future[]

    // tag::regular[]
    @Post
    HttpResponse<Person> save(@Body Person person) {
        inMemoryDatastore.put(person.getFirstName(), person)
        HttpResponse.created(person)
    }
    // end::regular[]

    // tag::localError[]
    @Error
    HttpResponse<JsonError> jsonError(HttpRequest request, JsonParseException e) { // <1>
        JsonError error = new JsonError("Invalid JSON: " + e.message) // <2>
                .link(Link.SELF, Link.of(request.uri))

        HttpResponse.<JsonError>status(HttpStatus.BAD_REQUEST, "Fix Your JSON")
                .body(error) // <3>
    }
    // end::localError[]

    @Get("/error")
    String throwError() {
        throw new RuntimeException("Something went wrong")
    }

    // tag::globalError[]
    @Error(global = true) // <1>
    HttpResponse<JsonError> error(HttpRequest request, Throwable e) {
        JsonError error = new JsonError("Bad Things Happened: " + e.message) // <2>
                .link(Link.SELF, Link.of(request.uri))

        HttpResponse.<JsonError>serverError()
                .body(error) // <3>
    }
    // end::globalError[]

    // tag::statusError[]
    @Error(status = HttpStatus.NOT_FOUND)
    HttpResponse<JsonError> notFound(HttpRequest request) { // <1>
        JsonError error = new JsonError("Person Not Found") // <2>
                .link(Link.SELF, Link.of(request.uri))

        HttpResponse.<JsonError>notFound()
                .body(error) // <3>
    }
    // end::statusError[]

// tag::endclass[]
}
// end::endclass[]
