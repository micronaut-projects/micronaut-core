import java
from typing import Annotated

from jakarta.inject import Inject
from org.junit.jupiter.api import Test

from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest

from .NamedRetryBookService import NamedRetryBookService

UncheckedIOException = java.type("java.io.UncheckedIOException")


@MicronautTest
@Property(name="spec.name", value="NamedRetrySpec")
@Property(name="micronaut.retry.policies.books.attempts", value="5")
@Property(name="micronaut.retry.policies.books.delay", value="1ms")
class NamedRetrySpec:
    service: Annotated[NamedRetryBookService, Inject] = None

    @Test
    def test_named_retry_policy(self):
        service = self.service

        assert service.find_book("The Stand").get_title() == "The Stand"
        assert service.reset() == 5

        try:
            service.get_book("The Stand")
            assert False, "expected an UncheckedIOException"
        except UncheckedIOException:
            pass
        assert service.reset() == 2
