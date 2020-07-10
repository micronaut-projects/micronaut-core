/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.server.json;

import com.fasterxml.jackson.core.JsonParseException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.*;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Requires(property = "spec.name", value = "PersonControllerSpec")
// tag::class[]
@Controller("/people")
public class PersonController {

    Map<String, Person> inMemoryDatastore = new ConcurrentHashMap<>();
// end::class[]

    @Get
    public Collection<Person> index() {
        return inMemoryDatastore.values();
    }

    @Get("/{name}")
    public Maybe<Person> get(String name) {
        if (inMemoryDatastore.containsKey(name)) {
            return Maybe.just(inMemoryDatastore.get(name));
        }
        return Maybe.empty();
    }

    // tag::single[]
    @Post("/saveReactive")
    public Single<HttpResponse<Person>> save(@Body Single<Person> person) { // <1>
        return person.map(p -> {
                    inMemoryDatastore.put(p.getFirstName(), p); // <2>
                    return HttpResponse.created(p); // <3>
                }
        );
    }
    // end::single[]

    // tag::args[]
    @Post("/saveWithArgs")
    public HttpResponse<Person> save(String firstName, String lastName, Optional<Integer> age) {
        Person p = new Person(firstName, lastName);
        age.ifPresent(p::setAge);
        inMemoryDatastore.put(p.getFirstName(), p);
        return HttpResponse.created(p);
    }
    // end::args[]

    // tag::future[]
    @Post("/saveFuture")
    public CompletableFuture<HttpResponse<Person>> save(@Body CompletableFuture<Person> person) {
        return person.thenApply(p -> {
                    inMemoryDatastore.put(p.getFirstName(), p);
                    return HttpResponse.created(p);
                }
        );
    }
    // end::future[]

    // tag::regular[]
    @Post
    public HttpResponse<Person> save(@Body Person person) {
        inMemoryDatastore.put(person.getFirstName(), person);
        return HttpResponse.created(person);
    }
    // end::regular[]

    // tag::localError[]
    @Error
    public HttpResponse<JsonError> jsonError(HttpRequest request, JsonParseException jsonParseException) { // <1>
        JsonError error = new JsonError("Invalid JSON: " + jsonParseException.getMessage()) // <2>
                .link(Link.SELF, Link.of(request.getUri()));

        return HttpResponse.<JsonError>status(HttpStatus.BAD_REQUEST, "Fix Your JSON")
                .body(error); // <3>
    }
    // end::localError[]

    @Get("/error")
    public String throwError() {
        throw new RuntimeException("Something went wrong");
    }

    // tag::globalError[]
    @Error(global = true) // <1>
    public HttpResponse<JsonError> error(HttpRequest request, Throwable e) {
        JsonError error = new JsonError("Bad Things Happened: " + e.getMessage()) // <2>
                .link(Link.SELF, Link.of(request.getUri()));

        return HttpResponse.<JsonError>serverError()
                .body(error); // <3>
    }
    // end::globalError[]

    // tag::statusError[]
    @Error(status = HttpStatus.NOT_FOUND)
    public HttpResponse<JsonError> notFound(HttpRequest request) { // <1>
        JsonError error = new JsonError("Person Not Found") // <2>
                .link(Link.SELF, Link.of(request.getUri()));

        return HttpResponse.<JsonError>notFound()
                .body(error); // <3>
    }
    // end::statusError[]

// tag::endclass[]
}
// end::endclass[]
