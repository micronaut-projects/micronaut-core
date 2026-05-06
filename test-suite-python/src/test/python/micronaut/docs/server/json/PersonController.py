from typing import Annotated

import java
from micronaut.context.annotation import Requires
from micronaut.http import HttpRequest, HttpResponse
from micronaut.http.annotation import Body, Controller, Error, Get, Post
from micronaut.http.hateoas import JsonError, Link
from micronaut.json import JsonSyntaxException

from .Person import Person

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
ConcurrentHashMap = java.type("java.util.concurrent.ConcurrentHashMap")
HttpStatus = java.type("io.micronaut.http.HttpStatus")
Mono = java.type("reactor.core.publisher.Mono")
Optional = java.type("java.util.Optional")
Publisher = java.type("org.reactivestreams.Publisher")
RuntimeException = java.type("java.lang.RuntimeException")
Throwable = java.type("java.lang.Throwable")


@Requires(property="spec.name", value="PersonControllerSpec")
# tag::class[]
@Controller("/people")
class PersonController:
    def __init__(self):
        self.inMemoryDatastore = ConcurrentHashMap()
# end::class[]

    @Get
    def index(self):
        return self.inMemoryDatastore.values()

    @Get("/{name}")
    # TODO: Re-enable @SingleResult when Python can import io.micronaut.core.async.annotation.SingleResult.
    # @SingleResult
    def get(self, name: str) -> Publisher:
        if self.inMemoryDatastore.containsKey(name):
            return Mono.just(self.inMemoryDatastore.get(name))
        return Mono.empty()

    # tag::single[]
    @Post("/saveReactive")
    # TODO: Re-enable @SingleResult when Python can import io.micronaut.core.async.annotation.SingleResult.
    # @SingleResult
    def saveReactive(self, person: Annotated[Publisher, Body]) -> Publisher:  # <1>
        def save_reactive_person(p):
            self.inMemoryDatastore.put(self.firstName(p), p)  # <2>
            return HttpResponse.created(p)  # <3>

        return getattr(Mono, "from")(person).map(save_reactive_person)
    # end::single[]

    # tag::args[]
    @Post("/saveWithArgs")
    def saveWithArgs(self, firstName: str, lastName: str, age: Optional) -> HttpResponse:
        p = Person(firstName, lastName)
        if age.isPresent():
            p.age = age.get()
        self.inMemoryDatastore.put(self.firstName(p), p)
        return HttpResponse.created(p)
    # end::args[]

    # tag::future[]
    @Post("/saveFuture")
    def saveFuture(self, person: Annotated[CompletableFuture, Body]) -> CompletableFuture:
        def save_future_person(p):
            self.inMemoryDatastore.put(self.firstName(p), p)
            return HttpResponse.created(p)

        return person.thenApply(save_future_person)
    # end::future[]

    # tag::regular[]
    @Post()
    def save(self, person: Annotated[Person, Body]) -> HttpResponse:
        self.inMemoryDatastore.put(self.firstName(person), person)
        return HttpResponse.created(person)
    # end::regular[]

    def firstName(self, person) -> str:
        if hasattr(person, "getFirstName"):
            return person.getFirstName()
        if hasattr(person, "firstName"):
            return person.firstName
        if hasattr(person, "get"):
            return person.get("firstName")
        return person["firstName"]

    # tag::localError[]
    @Error
    def jsonError(self, request: HttpRequest, e: JsonSyntaxException) -> HttpResponse:  # <1>
        error = JsonError("Invalid JSON: " + e.getMessage()).link(  # <2>
            Link.SELF, Link.of(request.getUri())
        )

        return HttpResponse.status(HttpStatus.BAD_REQUEST, "Fix Your JSON").body(error)  # <3>
    # end::localError[]

    @Get("/error")
    def throwError(self) -> str:
        raise RuntimeException("Something went wrong")

    # tag::globalError[]
    @Error(**{"global": True})  # <1>
    def error(self, request: HttpRequest, e: Throwable) -> HttpResponse:
        error = JsonError("Bad Things Happened: " + e.getMessage()).link(  # <2>
            Link.SELF, Link.of(request.getUri())
        )

        return HttpResponse.serverError().body(error)  # <3>
    # end::globalError[]

    # tag::statusError[]
    @Error(status=HttpStatus.NOT_FOUND)
    def notFound(self, request: HttpRequest) -> HttpResponse:  # <1>
        error = JsonError("Person Not Found").link(  # <2>
            Link.SELF, Link.of(request.getUri())
        )

        return HttpResponse.notFound().body(error)  # <3>
    # end::statusError[]
# tag::endclass[]
# end::endclass[]
