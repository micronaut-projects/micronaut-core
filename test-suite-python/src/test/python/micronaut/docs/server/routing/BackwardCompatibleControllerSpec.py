from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Property(name="spec.name", value="BackwardCompatibleControllerSpec")
@MicronautTest
class BackwardCompatibleControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_hello_world_response(self):
        response = self.client.toBlocking().retrieve("/hello/World")
        assert response == "Hello, World"

        response = self.client.toBlocking().retrieve("/hello/person/John")
        assert response == "Hello, John"
