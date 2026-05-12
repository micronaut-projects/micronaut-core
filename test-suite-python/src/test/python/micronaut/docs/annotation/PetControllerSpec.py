from typing import Annotated

import java
from jakarta.inject import Inject
from jakarta.validation import ConstraintViolationException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .PetClient import PetClient

Mono = java.type("reactor.core.publisher.Mono")


@MicronautTest
class PetControllerSpec:
    client: Annotated[PetClient, Inject]

    @Test
    def testPostPet(self) -> None:
        # tag::post[]
        pet = Mono.from_(self.client.save("Dino", 10)).block()

        assert pet.name == "Dino"
        assert pet.age == 10
        # end::post[]

    @Test
    def testPostPetValidation(self) -> None:
        try:
            Mono.from_(self.client.save("Fred", -1)).block()
        except ConstraintViolationException as ex:
            assert str(ex.getMessage()) == "save.age: must be greater than or equal to 1"
        else:
            assert False
