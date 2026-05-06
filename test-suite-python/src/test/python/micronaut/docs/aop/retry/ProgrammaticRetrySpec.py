import java

from org.junit.jupiter.api import Disabled, Test

from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest

from .ProgrammaticBookService import ProgrammaticBookService

Mono = java.type("reactor.core.publisher.Mono")


@MicronautTest
class ProgrammaticRetrySpec:
    @Test
    @Disabled("Python Java SAM conversion for RetryOperations suppliers is not validated yet")
    def test_programmatic_retry_examples(self):
        context = ApplicationContext.run({"spec.name": "ProgrammaticRetrySpec"})
        try:
            service = context.getBean(ProgrammaticBookService)

            service.reset()
            assert service.list_books()[0].get_title() == "The Stand"

            service.reset()
            assert getattr(Mono, "from")(service.stream_books()).block().get_title() == "The Stand"

            service.reset()
            assert service.find_book("The Stand").toCompletableFuture().get().get_title() == "The Stand"

            service.reset()
            assert service.find_book_with_circuit_breaker("The Stand").get_title() == "The Stand"
        finally:
            context.close()
