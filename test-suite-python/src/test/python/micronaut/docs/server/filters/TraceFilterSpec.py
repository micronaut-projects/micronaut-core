from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.filter", value="TraceFilter")
@MicronautTest
class TraceFilterSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def testTraceFilter(self):
        response = self.client.toBlocking().exchange(HttpRequest.GET("/hello"))

        assert response.getHeaders().get("X-Trace-Enabled") == "true"
