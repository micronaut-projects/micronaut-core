from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Argument = java.type("io.micronaut.core.type.Argument")
String = java.type("java.lang.String")


@Property(name="spec.name", value="PropagatedContextSpec")
@MicronautTest
class PropagatedContextTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def testMonoRequest(self) -> None:
        hello = self.client.toBlocking().retrieve(HttpRequest.GET("/hello?name=Dean"), Argument.of(String))
        assert hello == "Hello, Dean"
