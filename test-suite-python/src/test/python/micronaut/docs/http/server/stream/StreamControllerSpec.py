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
class StreamControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_receiving_a_stream(self):
        response = self.client.toBlocking().retrieve(HttpRequest.GET("/stream/write"), String)

        assert response == "test"

    @Test
    def test_returning_a_stream(self):
        body = "My body"
        response = self.client.toBlocking().retrieve(
            HttpRequest.POST("/stream/read", body).contentType(MediaType.TEXT_PLAIN_TYPE),
            String,
        )

        assert response == body
