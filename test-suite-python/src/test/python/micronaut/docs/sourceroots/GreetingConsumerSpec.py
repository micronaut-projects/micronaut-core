from typing import Annotated

from jakarta.inject import Inject, Singleton
from micronaut.docs.sourceroots import GreetingService
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Singleton
class GreetingConsumer:
    """A bean of the test sources depending on a bean of the main sources of the same package."""

    def __init__(self, service: GreetingService):
        self.service = service

    def greet(self, name: str) -> str:
        return self.service.greet(name)


@MicronautTest
class GreetingConsumerSpec:
    consumer: Annotated[GreetingConsumer, Inject] = None

    @Test
    def test_consumer_greets(self) -> None:
        assert self.consumer.greet("Python") == "Hello Python"
