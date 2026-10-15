from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

HttpStatus = java.type("io.micronaut.http.HttpStatus")
InvocationCounter = java.type("example.InvocationCounter")


@MicronautTest
class InvocationCountSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_module_route_runs_once_per_request(self):
        blocking = self.client.toBlocking()
        start = InvocationCounter.get()
        first = blocking.retrieve(HttpRequest.POST("/invocation-count/increment", ""))
        second = blocking.retrieve(HttpRequest.POST("/invocation-count/increment", ""))

        assert int(first) == start + 1
        assert int(second) == start + 2
        assert InvocationCounter.get() == start + 2

    @Test
    def test_module_route_returning_none_runs_once_per_request(self):
        start = InvocationCounter.get()
        try:
            self.client.toBlocking().exchange(HttpRequest.GET("/invocation-count/none"))
            assert False
        except HttpClientResponseException as e:
            response = e.getResponse()

        assert response.getStatus() == HttpStatus.NOT_FOUND
        assert InvocationCounter.get() == start + 1
