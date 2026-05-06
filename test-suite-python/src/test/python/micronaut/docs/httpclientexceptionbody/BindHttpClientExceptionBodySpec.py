from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.core.type import Argument
from micronaut.http import HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

BookClass = java.type("micronaut.docs.httpclientexceptionbody.Book")
CustomErrorClass = java.type("micronaut.docs.httpclientexceptionbody.CustomError")
OtherErrorClass = java.type("micronaut.docs.httpclientexceptionbody.OtherError")


@Property(name="spec.name", value="BindHttpClientExceptionBodySpec")
@MicronautTest
class BindHttpClientExceptionBodySpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    # tag::test[]
    @Test
    @Disabled("Python HTTP client returns generated Java wrapper objects for bound error bodies instead of Python dataclass objects")
    def afterAnHttpClientExceptionTheResponseBodyCanBeBoundToAPOJO(self):
        try:
            self.client.toBlocking().exchange(
                HttpRequest.GET("/books/1680502395"),
                Argument.of(BookClass),  # <1>
                Argument.of(CustomErrorClass),  # <2>
            )
            assert False
        except HttpClientResponseException as e:
            assert e.getResponse().getStatus() == HttpStatus.UNAUTHORIZED
            jsonError = e.getResponse().getBody(CustomErrorClass)
            assert jsonError.isPresent()
            assert jsonError.get().status == 401
            assert jsonError.get().error == "Unauthorized"
            assert jsonError.get().message == "No message available"
            assert jsonError.get().path == "/books/1680502395"
    # end::test[]

    @Test
    @Disabled("Python error-body binding does not currently reproduce the Java bind-failure behavior for dataclasses without defaults")
    def testExceptionBindingErrorResponse(self):
        try:
            self.client.toBlocking().exchange(
                HttpRequest.GET("/books/1680502395"),
                Argument.of(BookClass),
                Argument.of(OtherErrorClass),
            )
            assert False
        except HttpClientResponseException as e:
            assert e.getResponse().getStatus() == HttpStatus.UNAUTHORIZED
            jsonError = e.getResponse().getBody(OtherErrorClass)
            assert jsonError is not None
            assert not jsonError.isPresent()

    @Test
    @Disabled("GraalPy exception matching fails while catching the HTTP client response bind error")
    def verifyBindErrorIsThrown(self):
        try:
            self.client.toBlocking().exchange(
                HttpRequest.GET("/books/1491950358"),
                Argument.of(BookClass),
                Argument.of(CustomErrorClass),
            )
            assert False
        except HttpClientResponseException as e:
            assert e.getResponse().getStatus() == HttpStatus.OK
            assert e.getMessage().startswith("Error decoding HTTP response body")
