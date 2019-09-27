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

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Controller("/people")
class PersonController {

    Map<String, Person> inMemoryDatastore = new LinkedHashMap<>();
// end::class[]

    @Get
    Collection<Person> index() {
        inMemoryDatastore.values()
    }

    @Get("/{name}")
    Maybe<Person> get(String name) {
        if (inMemoryDatastore.containsKey(name)) {
            return Maybe.just(inMemoryDatastore.get(name))
        }
        Maybe.empty()
    }

    // tag::single[]
    @Post
    Single<HttpResponse<Person>> save(@Body Single<Person> person) { // <1>
        person.map({ p ->
            inMemoryDatastore.put(p.getFirstName(), p) // <2>
            HttpResponse.created(p) // <3>
        })
    }
    // end::single[]


    @Post("/saveWithArgs")
    // tag::args[]
    HttpResponse<Person> save(String firstName, String lastName, Optional<Integer> age) {
        Person p = new Person(firstName, lastName)
        age.ifPresent({ Integer i -> p.setAge(i) })
        inMemoryDatastore.put(p.getFirstName(), p)
        HttpResponse.created(p)
    }
    // end::args[]

    // tag::future[]
    CompletableFuture<HttpResponse<Person>> save(@Body CompletableFuture<Person> person) {
        person.thenApply({ p ->
            inMemoryDatastore.put(p.getFirstName(), p)
            HttpResponse.created(p)
        })
    }
    // end::future[]

    // tag::regular[]
    HttpResponse<Person> save(@Body Person person) {
        inMemoryDatastore.put(person.getFirstName(), person)
        HttpResponse.created(person)
    }
    // end::regular[]

    // tag::localError[]
    @Error
    public HttpResponse<JsonError> jsonError(HttpRequest request, JsonParseException jsonParseException) { // <1>
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

    @Error // <1>
    // tag::globalError[]
    HttpResponse<JsonError> error(HttpRequest request, Throwable e) {
        JsonError error = new JsonError("Bad Things Happened: " + e.getMessage()) // <2>
                .link(Link.SELF, Link.of(request.getUri()))

        HttpResponse.<JsonError>serverError()
                .body(error) // <3>
    }
    // end::globalError[]

    @Error(status = HttpStatus.NOT_FOUND)
    // tag::statusError[]
    HttpResponse<JsonError> notFound(HttpRequest request) { // <1>
        JsonError error = new JsonError("Page Not Found") // <2>
                .link(Link.SELF, Link.of(request.getUri()))

        HttpResponse.<JsonError>notFound()
                .body(error) // <3>
    }
    // end::statusError[]
}
