from typing import Annotated

from jakarta.inject import Inject
from jakarta.validation import ConstraintViolationException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.validation.validator import Validator
from org.junit.jupiter.api import Test

from .Person import Person
from .PersonService import PersonCaller, PersonService


@MicronautTest
class ImplicitValidationTest:
    service: Annotated[PersonService, Inject]
    caller: Annotated[PersonCaller, Inject]
    validator: Annotated[Validator, Inject]

    @Test
    def constrained_parameter_is_validated(self) -> None:
        assert self.service.say_hello("Fred") == "Hello Fred"
        try:
            self.service.say_hello("")
        except ConstraintViolationException as exception:
            assert exception.getMessage() == "say_hello.name: must not be blank"
        else:
            assert False, "ConstraintViolationException expected"

    @Test
    def valid_dataclass_parameter_is_cascaded(self) -> None:
        assert self.service.greet(Person("Fred")) == "Hello Fred"
        try:
            self.service.greet(Person(""))
        except ConstraintViolationException as exception:
            assert exception.getMessage() == "greet.person.name: must not be blank"
        else:
            assert False, "ConstraintViolationException expected"

    @Test
    def valid_dataclass_parameter_is_cascaded_for_a_python_caller(self) -> None:
        assert self.caller.greet(Person("Bob")) == "Hello Bob"
        try:
            self.caller.greet(Person(""))
        except ConstraintViolationException as exception:
            assert exception.getMessage() == "greet.person.name: must not be blank"
        else:
            assert False, "ConstraintViolationException expected"

    @Test
    def constrained_return_value_is_validated(self) -> None:
        assert self.service.name_of(Person("Fred")) == "Fred"
        try:
            self.service.name_of(Person(""))
        except ConstraintViolationException as exception:
            assert exception.getMessage() == "name_of.<return value>: must not be blank"
        else:
            assert False, "ConstraintViolationException expected"

    @Test
    def introspected_dataclass_fields_are_validated(self) -> None:
        violations = self.validator.validate(Person(""))
        messages = [violation.getPropertyPath().toString() + ": " + violation.getMessage() for violation in violations]
        assert messages == ["name: must not be blank"]
        assert self.validator.validate(Person("Fred")).isEmpty()
