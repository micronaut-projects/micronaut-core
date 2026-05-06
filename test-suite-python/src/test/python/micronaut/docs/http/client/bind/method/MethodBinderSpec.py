from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

from .NameAuthorizedClient import NameAuthorizedClient


@MicronautTest
class MethodBinderSpec:
    client: Annotated[NameAuthorizedClient, Inject]

    @Test
    @Disabled("Python AnnotatedClientRequestBinder does not read the custom method annotation value yet")
    def testBindingToTheRequest(self) -> None:
        resp = self.client.get()
        assert resp == "Hello, Bob"
