from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

from .PetClient import PetClient

ConstraintViolationException = java.type("jakarta.validation.ConstraintViolationException")
Mono = java.type("reactor.core.publisher.Mono")


@MicronautTest
class PetControllerSpec:
    client: Annotated[PetClient, Inject]

    @Test
    @Disabled("Python HTTP client returns generated Java wrapper objects for Python dataclass responses instead of Python objects")
    def testPostPet(self) -> None:
        # tag::post[]
        pet = getattr(Mono, "from")(self.client.save("Dino", 10)).block()

        assert pet.name == "Dino"
        assert pet.age == 10
        # end::post[]

    @Test
    @Disabled("GraalPy exception matching fails while catching the Java validation exception")
    def testPostPetValidation(self) -> None:
        try:
            getattr(Mono, "from")(self.client.save("Fred", -1)).block()
            assert False
        except ConstraintViolationException as ex:
            assert str(ex.getMessage()) == "save.age: must be greater than or equal to 1"
