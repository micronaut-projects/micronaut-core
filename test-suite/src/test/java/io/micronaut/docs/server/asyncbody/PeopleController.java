package io.micronaut.docs.server.asyncbody;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.body.AsyncRequestBody;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
// end::imports[]

@Requires(property = "spec.name", value = "PeopleControllerTest")
// tag::class[]
@Controller("/people")
public class PeopleController {

    private final List<Person> people = new CopyOnWriteArrayList<>();
// end::class[]

    // tag::body[]
    @Post
    public CompletionStage<HttpResponse<Person>> save(AsyncRequestBody body) { // <1>
        return body.body(Person.class) // <2>
            .thenCompose(this::store) // <3>
            .thenApply(HttpResponse::created);
    }
    // end::body[]

    // tag::elements[]
    @Post(uri = "/import", consumes = {MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_STREAM}) // <1>
    public CompletionStage<String> importPeople(AsyncRequestBody body) {
        AtomicInteger count = new AtomicInteger();
        return body.elements(Person.class) // <2>
            .forEach(person -> store(person).thenRun(count::incrementAndGet)) // <3>
            .thenApply(done -> "Imported " + count.get()); // <4>
    }
    // end::elements[]

    // tag::text[]
    @Post(uri = "/notes", consumes = MediaType.TEXT_PLAIN)
    public CompletionStage<String> note(AsyncRequestBody body) {
        return body.text() // <1>
            .thenApply(text -> "Received " + text.length() + " characters");
    }
    // end::text[]

    // tag::store[]
    private CompletionStage<Person> store(Person person) {
        people.add(person); // e.g. an asynchronous database call
        return CompletableFuture.completedFuture(person);
    }
    // end::store[]

// tag::endclass[]
}
// end::endclass[]
