from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

HttpStatus = java.type("io.micronaut.http.HttpStatus")


@Property(name="spec.name", value="respondingnotfound")
@MicronautTest
class BooksSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    @Disabled("Python None controller results are not mapped to the Java-equivalent 404 response")
    def test_returning_null_returns_404(self):
        try:
            self.client.toBlocking().exchange(HttpRequest.GET("/books/stock/XXXXX"))
            assert False
        except HttpClientResponseException as e:
            response = e.getResponse()

        assert response.getStatus() == HttpStatus.NOT_FOUND

    @Test
    @Disabled("Python cannot express @SingleResult import from io.micronaut.core.async.annotation yet")
    def test_returning_mono_empty_returns_404(self):
        try:
            self.client.toBlocking().exchange(HttpRequest.GET("/books/maybestock/XXXXX"))
            assert False
        except HttpClientResponseException as e:
            response = e.getResponse()

        assert response.getStatus() == HttpStatus.NOT_FOUND
