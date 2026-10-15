from micronaut.context.annotation import Requires

# tag::imports[]
import java
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Controller, Post
from micronaut.http.body import AsyncRequestBody

from .Person import Person

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
CompletionStage = java.type("java.util.concurrent.CompletionStage")
# end::imports[]


@Requires(property="spec.name", value="PeopleControllerSpec")
# tag::class[]
@Controller("/people")
class PeopleController:
    def __init__(self):
        self.people = []
# end::class[]

    # tag::body[]
    @Post
    def save(self, body: AsyncRequestBody) -> CompletionStage:  # <1>
        return (body.body(Person)  # <2>
                .thenCompose(self.store)  # <3>
                .thenApply(lambda person: HttpResponse.created(person)))
    # end::body[]

    # tag::elements[]
    @Post(uri="/import", consumes=[MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_STREAM])  # <1>
    def importPeople(self, body: AsyncRequestBody) -> CompletionStage:
        count = [0]

        def save(person):
            count[0] += 1
            return self.store(person)

        return (body.elements(Person)  # <2>
                .forEach(save)  # <3>
                .thenApply(lambda done: f"Imported {count[0]}"))  # <4>
    # end::elements[]

    # tag::text[]
    @Post(uri="/notes", consumes=MediaType.TEXT_PLAIN)
    def note(self, body: AsyncRequestBody) -> CompletionStage:
        return (body.text()  # <1>
                .thenApply(lambda text: f"Received {len(text)} characters"))
    # end::text[]

    # tag::store[]
    def store(self, person: Person) -> CompletionStage:
        self.people.append(person)  # e.g. an asynchronous database call
        return CompletableFuture.completedFuture(person)
    # end::store[]

# tag::endclass[]
# end::endclass[]
