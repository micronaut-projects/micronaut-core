from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .PetClient import PetClient

Mono = java.type("reactor.core.publisher.Mono")


@MicronautTest
class PetRetrySpec:
    client: Annotated[PetClient, Inject]

    @Test
    def testFallback(self) -> None:
        pet = Mono.from_(self.client.save("Dino", 10)).block()

        assert pet.name == "Dino"
        assert pet.age == 10
