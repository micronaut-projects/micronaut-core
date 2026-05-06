from typing import Annotated

from micronaut.context.annotation import Requires
# tag::class[]
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Body, Consumes, Controller, Post

from .Person import Person


@Requires(property="spec.name", value="PersonControllerFormTest")
@Controller("/people")
class PersonController:

    inMemoryDatastore: dict[str, Person] = {}
# end::class[]

# tag::formbinding[]
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post()
    def save(self, person: Annotated[Person, Body]) -> HttpResponse:
        self.inMemoryDatastore[person.getFirstName()] = person
        return HttpResponse.created(person)
# end::formbinding[]

# tag::formsaveWithArgs[]
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/saveWithArgs")
    def saveWithArgs(self, firstName: str, lastName: str, age: int | None = None) -> HttpResponse:
        person = Person(firstName, lastName)
        if age is not None:
            person.setAge(age)
        self.inMemoryDatastore[person.getFirstName()] = person
        return HttpResponse.created(person)
# end::formsaveWithArgs[]


    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/saveWithArgsOptional")
    def saveWithArgsOptional(self, firstName: str, lastName: str, age: int | None = None) -> HttpResponse:
        person = Person(firstName, lastName)
        if age is not None:
            person.setAge(age)
        self.inMemoryDatastore[person.getFirstName()] = person
        return HttpResponse.created(person)


# tag::endclass[]
# end::endclass[]
