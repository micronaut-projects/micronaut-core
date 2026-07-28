from typing import Annotated

# tag::imports[]
from jakarta.inject import Named
from micronaut.core.async_.annotation import SingleResult
from micronaut.docs.ioc.beans.Person import Person
from micronaut.http.annotation import Controller, Get
from micronaut.scheduling import TaskExecutors

from .PersonService import PersonService
# end::imports[]

import java

ExecutorService = java.type("java.util.concurrent.ExecutorService")
Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")
Schedulers = java.type("reactor.core.scheduler.Schedulers")


# tag::class[]
@Controller("/subscribeOn/people")
class PersonController:
    def __init__(
        self,
        executorService: Annotated[ExecutorService, Named(TaskExecutors.IO)],  # <1>
        personService: PersonService,
    ):
        self.scheduler = Schedulers.fromExecutorService(executorService)
        self.personService = personService

    @Get("/{name}")
    @SingleResult
    def byName(self, name: str) -> Publisher:
        return (
            Mono.fromCallable(lambda: self.personService.findByName(name))  # <2>
            .subscribeOn(self.scheduler)  # <3>
        )
# end::class[]
