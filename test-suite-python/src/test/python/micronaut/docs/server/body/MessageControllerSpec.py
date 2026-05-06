from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.http import MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

HttpRequest = java.type("io.micronaut.http.HttpRequest")
String = java.type("java.lang.String")


@MicronautTest
class MessageControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_echo_response(self):
        body = "My Text"
        response = self.client.toBlocking().retrieve(
            HttpRequest.POST("/receive/echo", body).contentType(MediaType.TEXT_PLAIN_TYPE),
            String,
        )

        assert response == body

    @Test
    def test_echo_reactive_response(self):
        body = "My Text"
        response = self.client.toBlocking().retrieve(
            HttpRequest.POST("/receive/echo-publisher", body).contentType(MediaType.TEXT_PLAIN_TYPE),
            String,
        )

        assert response == body
