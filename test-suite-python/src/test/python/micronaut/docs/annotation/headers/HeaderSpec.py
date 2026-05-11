from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.context.annotation import Property
from org.junit.jupiter.api import Test

from .PetClient import PetClient

Mono = java.type("reactor.core.publisher.Mono")


@Property(name="pet.client.id", value="11")
@MicronautTest
class HeaderSpec:
    client: Annotated[PetClient, Inject]

    @Test
    def testSenderHeaders(self) -> None:
        pet = getattr(Mono, "from")(self.client.get("Fred")).block()

        assert pet is not None
        assert pet.age == 11
