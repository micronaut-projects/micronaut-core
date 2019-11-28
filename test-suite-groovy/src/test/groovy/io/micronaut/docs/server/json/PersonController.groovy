package io.micronaut.docs.server.json

import com.fasterxml.jackson.core.JsonParseException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.reactivex.Maybe
import io.reactivex.Single

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

// tag::class[]
@Controller("/people")
class PersonController {

    ConcurrentHashMap<String, Person> inMemoryDatastore = [:]
// end::class[]

    @Get
    Collection<Person> index() {
        inMemoryDatastore.values()
    }

    @Get("/{name}")
    Maybe<Person> get(String name) {
        Person person = inMemoryDatastore.get(name)
        if (person != null) {
            Maybe.just(person)
        } else {
            Maybe.empty()
        }
    }

    // tag::single[]
    @Post("/saveReactive")
    Single<HttpResponse<Person>> save(@Body Single<Person> person) { // <1>
        person.map({ p ->
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
    HttpResponse<JsonError> jsonError(HttpRequest request, JsonParseException jsonParseException) { // <1>
        JsonError error = new JsonError("Invalid JSON: " + jsonParseException.getMessage()) // <2>
                .link(Link.SELF, Link.of(request.getUri()))

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
        JsonError error = new JsonError("Bad Things Happened: " + e.getMessage()) // <2>
                .link(Link.SELF, Link.of(request.getUri()))

        HttpResponse.<JsonError>serverError()
                .body(error) // <3>
    }
    // end::globalError[]

    // tag::statusError[]
    @Error(status = HttpStatus.NOT_FOUND)
    HttpResponse<JsonError> notFound(HttpRequest request) { // <1>
        JsonError error = new JsonError("Person Not Found") // <2>
                .link(Link.SELF, Link.of(request.getUri()))

        HttpResponse.<JsonError>notFound()
                .body(error) // <3>
    }
    // end::statusError[]

// tag::endclass[]
}
// end::endclass[]
