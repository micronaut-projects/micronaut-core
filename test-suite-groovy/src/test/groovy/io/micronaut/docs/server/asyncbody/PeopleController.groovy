package io.micronaut.docs.server.asyncbody

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.body.AsyncRequestBody

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
// end::imports[]

@Requires(property = "spec.name", value = "PeopleControllerSpec")
// tag::class[]
@Controller("/people")
class PeopleController {

    private final List<Person> people = new CopyOnWriteArrayList<>()
// end::class[]

    // tag::body[]
    @Post
    CompletionStage<HttpResponse<Person>> save(AsyncRequestBody body) { // <1>
        body.body(Person) // <2>
            .thenCompose { Person person -> store(person) } // <3>
            .thenApply { Person person -> HttpResponse.created(person) }
    }
    // end::body[]

    // tag::elements[]
    @Post(uri = "/import", consumes = [MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_STREAM]) // <1>
    CompletionStage<String> importPeople(AsyncRequestBody body) {
        AtomicInteger count = new AtomicInteger()
        body.elements(Person) // <2>
            .forEach { Person person -> store(person).thenRun { count.incrementAndGet() } } // <3>
            .thenApply { "Imported ${count.get()}".toString() } // <4>
    }
    // end::elements[]

    // tag::text[]
    @Post(uri = "/notes", consumes = MediaType.TEXT_PLAIN)
    CompletionStage<String> note(AsyncRequestBody body) {
        body.text() // <1>
            .thenApply { String text -> "Received ${text.length()} characters".toString() }
    }
    // end::text[]

    // tag::store[]
    private CompletionStage<Person> store(Person person) {
        people.add(person) // e.g. an asynchronous database call
        CompletableFuture.completedFuture(person)
    }
    // end::store[]

// tag::endclass[]
}
// end::endclass[]
