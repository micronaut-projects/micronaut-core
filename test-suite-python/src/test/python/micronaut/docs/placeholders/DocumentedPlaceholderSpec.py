from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .BookClient import BookClient


@Property(name="spec.name", value="DocumentedPlaceholderSpec")
@MicronautTest
class DocumentedPlaceholderSpec:
    client: Annotated[BookClient, Inject]

    @Test
    def aDocumentedPlaceholderIsImplementedByTheClient(self):
        assert self.client.find(7) == "Book 7"
