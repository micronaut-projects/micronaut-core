from typing import Annotated

from jakarta.inject import Inject
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest
class MessageControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def test_request(self):
        body = self.client.toBlocking().retrieve("/request/hello?name=John")

        assert body is not None
        assert body == "Hello John!!"

        body = self.client.toBlocking().retrieve("/request/hello-static?name=John")

        assert body is not None
        assert body == "Hello John!!"

        body = self.client.toBlocking().retrieve("/request/hello-reactor?name=John")

        assert body is not None
        assert body == "Hello John!!"
