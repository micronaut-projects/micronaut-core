# tag::test[]
from typing import Annotated

from jakarta.inject import Inject
from micronaut.docs.sourceroots.greeting_service import GreetingService  # <1>
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest
class GreetingServiceSpec:
    service: Annotated[GreetingService, Inject] = None  # <2>

    @Test
    def test_greets(self) -> None:
        assert self.service.greet("Python") == "Hello Python"
# end::test[]
