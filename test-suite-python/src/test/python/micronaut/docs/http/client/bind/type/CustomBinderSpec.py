from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .Metadata import Metadata
from .MetadataClient import MetadataClient


@MicronautTest
class CustomBinderSpec:
    client: Annotated[MetadataClient, Inject]

    @Test
    def testBindingToTheRequest(self) -> None:
        resp = self.client.get(Metadata(3.6, 42))
        assert resp == "3.6"
