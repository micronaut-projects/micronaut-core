from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

HttpClientResponseException = java.type("io.micronaut.http.client.exceptions.HttpClientResponseException")
HttpStatus = java.type("io.micronaut.http.HttpStatus")


@Property(name="spec.name", value="ReportControllerTest")
@MicronautTest
class ReportControllerTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def the_routes_answer_within_their_timeout(self):
        blocking = self.client.toBlocking()
        assert blocking.retrieve(HttpRequest.GET("/reports/summary")) == "summary"
        assert blocking.retrieve(HttpRequest.GET("/reports/export")) == "export"
        assert blocking.retrieve(HttpRequest.GET("/reports/archive")) == "archive"
        try:
            blocking.retrieve(HttpRequest.GET("/reports/rebuild"))
            raise AssertionError("the route did not time out")
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.SERVICE_UNAVAILABLE
