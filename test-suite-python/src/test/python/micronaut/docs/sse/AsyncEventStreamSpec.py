from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, MediaType
from micronaut.http.client.annotation import Client
from micronaut.http.client import StreamingHttpClient
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

String = java.type("java.lang.String")
TimeUnit = java.type("java.util.concurrent.TimeUnit")
PythonAsyncioRuntime = java.type("io.micronaut.context.python.PythonAsyncioRuntime")


@Property(name="spec.name", value="AsyncEventStreamSpec")
@MicronautTest
class AsyncEventStreamSpec:
    httpClient: Annotated[StreamingHttpClient, Inject, Client("/")]

    @Test
    def readEventsAsTheyArrive(self):
        result = PythonAsyncioRuntime.toCompletionStage(self.readEvents())
        assert result.toCompletableFuture().get(10, TimeUnit.SECONDS) == "abc-123"

    # tag::async[]
    async def readEvents(self) -> str:
        client = self.httpClient.toAsyncStreaming()  # <1>
        request = HttpRequest.POST("/mcp", '{"method":"ping"}') \
            .contentType(MediaType.APPLICATION_JSON_TYPE) \
            .accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_EVENT_STREAM_TYPE)
        response = await client.exchangeEventStream(request, String)  # <2>
        elements = response.body()  # <3>

        async def events():
            try:
                while True:
                    event = await elements.next()
                    if event.isEmpty():
                        break
                    yield event.get()
            finally:
                elements.close()

        messages = []
        async for event in events():  # <4>
            messages.append(event.getData())
        session_id = response.getHeaders().get("Mcp-Session-Id")  # <5>
        # end::async[]
        assert messages == ["progress", "done"]
        return session_id
