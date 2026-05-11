from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .MetadataClient import MetadataClient

LinkedHashMap = java.type("java.util.LinkedHashMap")


@MicronautTest
class AnnotationBinderSpec:
    client: Annotated[MetadataClient, Inject]

    @Test
    def testBindingToTheRequest(self) -> None:
        metadata = LinkedHashMap()
        metadata.put("version", 3.6)
        metadata.put("deploymentId", 42)
        resp = self.client.get(metadata)
        assert resp == "3.6"
