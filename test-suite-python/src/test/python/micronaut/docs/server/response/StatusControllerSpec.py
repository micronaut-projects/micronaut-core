from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

HttpRequest = java.type("io.micronaut.http.HttpRequest")
HttpStatus = java.type("io.micronaut.http.HttpStatus")
String = java.type("java.lang.String")


@Property(name="spec.name", value="httpstatus")
@MicronautTest
class StatusControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_status(self):
        response = self.client.toBlocking().exchange(HttpRequest.GET("/status"), String)
        body = response.getBody()

        assert response.getStatus() == HttpStatus.CREATED
        assert body.get() == "success"

        response = self.client.toBlocking().exchange(HttpRequest.GET("/status/http-response"), String)
        body = response.getBody()

        assert response.getStatus() == HttpStatus.CREATED
        assert body.get() == "success"

        response = self.client.toBlocking().exchange(HttpRequest.GET("/status/http-status"), String)

        assert response.getStatus() == HttpStatus.CREATED
