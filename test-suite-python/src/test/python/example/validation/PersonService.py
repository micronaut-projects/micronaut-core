from typing import Annotated

from jakarta.inject import Singleton
from jakarta.validation import Valid
from jakarta.validation.constraints import NotBlank

from .Person import Person


# A bean with constrained members is validated without an explicit @Validated, as a Java bean is.
@Singleton
class PersonService:
    def say_hello(self, name: Annotated[str, NotBlank]) -> str:
        return f"Hello {name}"

    def greet(self, person: Annotated[Person, Valid]) -> str:
        return f"Hello {person.name}"

    def name_of(self, person: Person) -> Annotated[str, NotBlank]:
        return person.name


@Singleton
class PersonCaller:
    def __init__(self, service: PersonService):
        self.service = service

    def greet(self, person: Person) -> str:
        return self.service.greet(person)
