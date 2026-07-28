# tag::imports[]
from micronaut.docs.http.server.reactive.PersonService import PersonService
from micronaut.docs.ioc.beans.Person import Person
from micronaut.http.annotation import Controller, Get
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn
# end::imports[]


# tag::class[]
@Controller("/executeOn/people")
class PersonController:
    def __init__(self, personService: PersonService):
        self.personService = personService

    @Get("/{name}")
    @ExecuteOn(TaskExecutors.IO)  # <1>
    def byName(self, name: str) -> Person:
        return self.personService.findByName(name)
# end::class[]
