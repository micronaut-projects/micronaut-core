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
from org.junit.jupiter.api import Test

Map = java.type("java.util.Map")
String = java.type("java.lang.String")


@Property(name="spec.name", value="ErrorRoutesTest")
@MicronautTest
class ErrorRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    def _fails(self, request) -> HttpClientResponseException:
        try:
            self.client.toBlocking().retrieve(request)
        except HttpClientResponseException as e:
            return e
        raise AssertionError("the request did not fail")

    @staticmethod
    def _body(error: HttpClientResponseException):
        return error.getResponse().getBody(Argument.mapOf(String, String)).orElseThrow()

    @Test
    def a_global_error_route(self):
        error = self._fails(HttpRequest.GET("/orders/5"))
        assert error.getStatus() == HttpStatus.NOT_FOUND
        assert self._body(error) == Map.of("error", "No order 5")

    @Test
    def a_global_status_route(self):
        error = self._fails(HttpRequest.GET("/nothing/here"))
        assert error.getStatus() == HttpStatus.NOT_FOUND
        assert self._body(error) == Map.of("error", "Nothing at /nothing/here")

    @Test
    def the_error_routes_of_a_group_are_local_to_its_routes(self):
        assert self.client.toBlocking().retrieve(HttpRequest.POST("/checkout/2", "")) == "ordered 2"
        invalid = self._fails(HttpRequest.POST("/checkout/0", ""))
        assert invalid.getStatus() == HttpStatus.BAD_REQUEST
        assert self._body(invalid) == Map.of("invalid", "quantity must be positive")
        conflict = self._fails(HttpRequest.POST("/checkout/11", ""))
        assert conflict.getStatus() == HttpStatus.CONFLICT
        assert self._body(conflict) == Map.of("conflict", "not enough stock")
        # the error route of the group does not answer for a route outside of it
        outside = self._fails(HttpRequest.GET("/pricing/0"))
        assert outside.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR
