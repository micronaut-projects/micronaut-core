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
    @Disabled("Python @Client Publisher return type is decoded as Object without Pet generic element metadata")
    def testPostPet(self) -> None:
        # tag::post[]
        pet = getattr(Mono, "from")(self.client.save("Dino", 10)).block()

        assert pet.name == "Dino"
        assert pet.age == 10
        # end::post[]

    @Test
    @Disabled("Python client-side validation metadata is not applied to the generated Publisher client method yet")
    def testPostPetValidation(self) -> None:
        try:
            getattr(Mono, "from")(self.client.save("Fred", -1)).block()
            assert False
        except ConstraintViolationException as ex:
            assert str(ex.getMessage()) == "save.age: must be greater than or equal to 1"
