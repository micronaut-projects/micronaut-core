import java
from typing import Annotated

from jakarta.inject import Inject
from org.junit.jupiter.api import Test

from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest

from .ProgrammaticBookService import ProgrammaticBookService

Mono = java.type("reactor.core.publisher.Mono")


@MicronautTest
@Property(name="spec.name", value="ProgrammaticRetrySpec")
class ProgrammaticRetrySpec:
    service: Annotated[ProgrammaticBookService, Inject] = None

    @Test
    def test_programmatic_retry_examples(self):
        service = self.service

        service.reset()
        assert service.list_books()[0].get_title() == "The Stand"

        service.reset()
        assert Mono.from_(service.stream_books()).block().get_title() == "The Stand"

        service.reset()
        assert service.find_book("The Stand").toCompletableFuture().get().get_title() == "The Stand"

        service.reset()
        assert service.find_book_with_circuit_breaker("The Stand").get_title() == "The Stand"
