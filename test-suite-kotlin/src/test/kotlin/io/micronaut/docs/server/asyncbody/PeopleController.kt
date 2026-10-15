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

@Requires(property = "spec.name", value = "PeopleControllerTest")
// tag::class[]
@Controller("/people")
class PeopleController {

    private val people = CopyOnWriteArrayList<Person>()
// end::class[]

    // tag::body[]
    @Post
    fun save(body: AsyncRequestBody): CompletionStage<HttpResponse<Person>> { // <1>
        return body.body(Person::class.java) // <2>
            .thenCompose { person -> store(person!!) } // <3>
            .thenApply { person -> HttpResponse.created(person) }
    }
    // end::body[]

    // tag::elements[]
    @Post(uri = "/import", consumes = [MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_STREAM]) // <1>
    fun importPeople(body: AsyncRequestBody): CompletionStage<String> {
        val count = AtomicInteger()
        return body.elements(Person::class.java) // <2>
            .forEach { person -> store(person).thenRun { count.incrementAndGet() } } // <3>
            .thenApply { "Imported ${count.get()}" } // <4>
    }
    // end::elements[]

    // tag::text[]
    @Post(uri = "/notes", consumes = [MediaType.TEXT_PLAIN])
    fun note(body: AsyncRequestBody): CompletionStage<String> {
        return body.text() // <1>
            .thenApply { text -> "Received ${text.length} characters" }
    }
    // end::text[]

    // tag::store[]
    private fun store(person: Person): CompletionStage<Person> {
        people.add(person) // e.g. an asynchronous database call
        return CompletableFuture.completedFuture(person)
    }
    // end::store[]

// tag::endclass[]
}
// end::endclass[]
