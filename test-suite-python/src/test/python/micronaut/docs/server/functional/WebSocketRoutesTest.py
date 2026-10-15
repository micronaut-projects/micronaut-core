from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.websocket import WebSocketClient
from org.junit.jupiter.api import Test

from .WebSocketRoutesClient import WebSocketRoutesClient

Flux = java.type("reactor.core.publisher.Flux")
RuntimeException = java.type("java.lang.RuntimeException")


@Property(name="spec.name", value="WebSocketRoutesTest")
@MicronautTest
class WebSocketRoutesTest:
    wsClient: Annotated[WebSocketClient, Inject, Client("/")]

    def _connect(self, path: str, token: str = None) -> WebSocketRoutesClient:
        request = HttpRequest.GET(path)
        if token is not None:
            request.header("X-Token", token)
        return Flux.from_(self.wsClient.connect(WebSocketRoutesClient, request)).blockFirst()

    @Test
    def the_handlers_answer_the_connection(self):
        client = self._connect("/echo/World")
        assert client.next() == "Hello World"
        client.send("hi")
        assert client.next() == "echo hi"
        client.close()

    @Test
    def the_filters_of_the_route_apply_to_the_upgrade(self):
        try:
            self._connect("/private")
            raise AssertionError("the upgrade was not rejected")
        except RuntimeException:
            pass
        client = self._connect("/private", "secret")
        assert client.next() == "welcome"
        client.close()

    @Test
    def the_messages_are_streams(self):
        ticks = self._connect("/ticks")
        assert ticks.next() == "tick 1"
        assert ticks.next() == "tick 2"
        assert ticks.next() == "tick 3"
        ticks.close()

        upper = self._connect("/upper")
        upper.send("hello")
        upper.send("world")
        assert upper.next() == "HELLO"
        assert upper.next() == "WORLD"
        upper.close()

    @Test
    def the_jobs_are_handled_concurrently(self):
        client = self._connect("/jobs")
        client.send("1")
        client.send("2")
        # up to 4 at the same time: in any order
        assert {client.next(), client.next()} == {"done 1", "done 2"}
        client.close()
