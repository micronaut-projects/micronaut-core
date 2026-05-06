from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.core.type import Argument
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

HttpStatus = java.type("io.micronaut.http.HttpStatus")
Map = java.type("java.util.Map")


@Property(name="spec.name", value="ExceptionHandlerSpec")
@MicronautTest
@Disabled("Python exception classes do not extend Java Throwable, so custom ExceptionHandler<T> cannot compile yet")
class ExceptionHandlerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_exception_is_handled(self):
        request = HttpRequest.GET("/books/stock/1234")

        try:
            self.client.toBlocking().exchange(request, Argument.of(int), Argument.of(Map))
            assert False
        except HttpClientResponseException as e:
            response = e.getResponse()

        body = response.getBody(Map).get()
        embedded = body.get("_embedded")
        message = embedded.get("errors")[0].get("message")

        assert response.getStatus() == HttpStatus.BAD_REQUEST
        assert message == "No stock available"
