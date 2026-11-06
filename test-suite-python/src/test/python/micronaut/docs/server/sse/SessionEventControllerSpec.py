from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.sse import SseClient
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Flux = java.type("reactor.core.publisher.Flux")
String = java.type("java.lang.String")


@Property(name="spec.name", value="SessionEventControllerSpec")
@MicronautTest
class SessionEventControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]
    sseClient: Annotated[SseClient, Inject, Client("/")]

    @Test
    def testNestedGenericResponseKeepsStatusAndHeader(self):
        response = self.client.toBlocking().exchange(
            HttpRequest.POST("/session-events", "\"hello\"").contentType(MediaType.APPLICATION_JSON),
            String
        )

        assert response.getStatus() == HttpStatus.OK
        assert response.header("Mcp-Session-Id") == "abc-123"
        body = response.body()
        assert "data: progress" in body, body
        assert "data: done" in body, body

    @Test
    def testNestedGenericResponseStreamsEvents(self):
        events = Flux.from_(
            self.sseClient.eventStream(
                HttpRequest.POST("/session-events", "\"hi\"").contentType(MediaType.APPLICATION_JSON),
                String
            )
        ).collectList().block()

        assert events.size() == 2, "events size: " + str(events.size())
        assert events.get(0).getData() == "progress"
        assert events.get(1).getData() == "done"
