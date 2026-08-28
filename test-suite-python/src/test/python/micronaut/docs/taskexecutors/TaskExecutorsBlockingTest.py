from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.uri import UriBuilder
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

String = java.type("java.lang.String")


@Property(name="spec.name", value="TaskExecutorsBlockingTest")
@MicronautTest
class TaskExecutorsBlockingTest:
    httpClient: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def testMethodAnnotatedWithTaskExecutorsBlocking(self):
        client = self.httpClient.toBlocking()
        request = HttpRequest.GET(
            UriBuilder.of("/hello").path("world").build()
        ).accept(MediaType.TEXT_PLAIN)
        response = client.exchange(request, String)

        assert response.status() == HttpStatus.OK
        txt = response.getBody(String)
        assert txt.isPresent()
        assert txt.get() == "Hello World"
