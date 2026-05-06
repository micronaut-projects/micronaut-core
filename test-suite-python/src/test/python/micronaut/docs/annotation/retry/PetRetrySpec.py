from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

from .PetClient import PetClient

Mono = java.type("reactor.core.publisher.Mono")


@MicronautTest
class PetRetrySpec:
    client: Annotated[PetClient, Inject]

    @Test
    @Disabled("Python HTTP client Publisher return types lose generic element metadata for fallback resolution")
    def testFallback(self) -> None:
        pet = getattr(Mono, "from")(self.client.save("Dino", 10)).block()

        assert pet.name == "Dino"
        assert pet.age == 10
