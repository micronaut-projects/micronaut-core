from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

String = java.type("java.lang.String")


@MicronautTest
class CurrentDateEndpointSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    @Disabled("Python management endpoint routing is not validated yet")
    def testReadCustomDateEndpoint(self) -> None:
        response = self.client.toBlocking().exchange("/date", String)
        assert response.code() == 200
