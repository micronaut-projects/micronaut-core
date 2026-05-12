from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

HttpHeaders = java.type("io.micronaut.http.HttpHeaders")
HttpRequest = java.type("io.micronaut.http.HttpRequest")
Point = java.type("micronaut.docs.server.binding.Point")


@Property(name="spec.name", value="PointControllerTest")
@MicronautTest
class PointControllerTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_json_with_no_at_body_endpoint(self):
        http_request = HttpRequest.POST("/point/no-body-json", "{\"x\":10,\"y\":20}").header(
            HttpHeaders.CONTENT_TYPE,
            MediaType.APPLICATION_JSON,
        )
        response = self.client.toBlocking().exchange(http_request, Point)

        self.assert_result(response.getBody().orElse(None))

    @Test
    def test_form_with_no_at_body_endpoint(self):
        http_request = HttpRequest.POST("/point/no-body-form", "x=10&y=20").header(
            HttpHeaders.CONTENT_TYPE,
            MediaType.APPLICATION_FORM_URLENCODED,
        )
        response = self.client.toBlocking().exchange(http_request, Point)

        self.assert_result(response.getBody().orElse(None))

    def assert_result(self, point):
        assert point is not None
        assert point.getX() == 10
        assert point.getY() == 20
