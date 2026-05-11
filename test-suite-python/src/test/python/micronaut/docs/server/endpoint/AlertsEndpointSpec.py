from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

String = java.type("java.lang.String")


@Property(name="spec.name", value="AlertsEndpointSpec")
@MicronautTest
class AlertsEndpointSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def testAddingAnAlert(self) -> None:
        try:
            self.client.toBlocking().exchange(
                HttpRequest.POST("/alerts", "First alert").contentType(MediaType.TEXT_PLAIN_TYPE),
                String,
            )
            assert False, "Expected sensitive endpoint request to fail"
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.UNAUTHORIZED
