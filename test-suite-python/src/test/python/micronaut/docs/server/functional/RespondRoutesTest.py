from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

String = java.type("java.lang.String")


@Property(name="spec.name", value="RespondRoutesTest")
@MicronautTest
class RespondRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def the_routes_answer_without_a_handler(self):
        http = self.client.toBlocking()
        ping = http.exchange(HttpRequest.GET("/ping"), String)
        assert ping.body() == "pong"
        assert ping.getHeaders().get("Cache-Control") == "max-age=60"
        # the client follows the redirect
        assert http.retrieve(HttpRequest.GET("/old-ping")) == "pong"
        assert http.retrieve(HttpRequest.GET("/visits")) == "visit 1"
        assert http.retrieve(HttpRequest.GET("/visits")) == "visit 2"
        assert http.retrieve(HttpRequest.GET("/greetings/World")) == "Hello World"
        try:
            http.retrieve(HttpRequest.POST("/legacy/webhook", "{}"))
            raise AssertionError("the request did not fail")
        except HttpClientResponseException as e:
            assert e.getStatus() == HttpStatus.GONE
