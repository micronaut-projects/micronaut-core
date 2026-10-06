from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, MediaType
from micronaut.http.client.annotation import Client
from micronaut.http.client.sse import SseClient
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
CopyOnWriteArrayList = java.type("java.util.concurrent.CopyOnWriteArrayList")
String = java.type("java.lang.String")
TimeUnit = java.type("java.util.concurrent.TimeUnit")


@Property(name="spec.name", value="AsyncSseClientSpec")
@MicronautTest
class AsyncSseClientSpec:
    sseClient: Annotated[SseClient, Inject, Client("/")]

    @Test
    def readEventsAsTheyArrive(self):
        # tag::async[]
        client = self.sseClient.toAsyncSse()  # <1>
        request = HttpRequest.POST("/mcp", '{"method":"ping"}') \
            .contentType(MediaType.APPLICATION_JSON_TYPE) \
            .accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_EVENT_STREAM_TYPE)
        messages = CopyOnWriteArrayList()

        def collect(event):  # <4>
            messages.add(event.getData())
            return CompletableFuture.completedStage(None)

        session_id = client.exchangeEventStream(request, String).thenCompose(  # <2>
            lambda response: response.body()  # <3>
            .forEach(collect)
            .thenApply(lambda done: response.getHeaders().get("Mcp-Session-Id")))  # <5>
        # end::async[]

        assert session_id.toCompletableFuture().get(10, TimeUnit.SECONDS) == "abc-123"
        assert list(messages) == ["progress", "done"]
