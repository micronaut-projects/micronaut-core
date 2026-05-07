from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

String = java.type("java.lang.String")


@MicronautTest
class WritableSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_render_template(self):
        assert self.client.toBlocking().retrieve("/template/welcome", String) == "Dear Fred Flintstone. Nice to meet you."

    @Test
    def test_the_correct_headers_are_applied(self):
        response = self.client.toBlocking().exchange("/template/welcome", String)

        assert response.getHeaders().contains("Date")
        assert response.getHeaders().contains("Content-Length")
