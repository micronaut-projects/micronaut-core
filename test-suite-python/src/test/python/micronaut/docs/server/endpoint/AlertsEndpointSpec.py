from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

String = java.type("java.lang.String")


@Property(name="spec.name", value="AlertsEndpointSpec")
@MicronautTest
class AlertsEndpointSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    @Disabled("Python management endpoint sensitivity handling is not validated yet")
    def testAddingAnAlert(self) -> None:
        self.client.toBlocking().exchange(
            HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE),
            String,
        )
