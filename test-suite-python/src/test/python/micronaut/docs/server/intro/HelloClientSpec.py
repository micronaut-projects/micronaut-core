# tag::imports[]
from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .HelloClient import HelloClient

Mono = java.type("reactor.core.publisher.Mono")
# end::imports[]


# tag::class[]
@MicronautTest  # <1>
class HelloClientSpec:
    client: Annotated[HelloClient, Inject]  # <2>

    @Test
    def testHelloWorldResponse(self) -> None:
        assert "Hello World" == getattr(Mono, "from")(self.client.hello()).block()  # <3>
# end::class[]
