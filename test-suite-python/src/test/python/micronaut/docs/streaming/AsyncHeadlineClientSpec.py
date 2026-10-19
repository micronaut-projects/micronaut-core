from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client import AsyncStreamingHttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .AsyncHeadlineClient import AsyncHeadlineClient

HeadlineClass = java.type("micronaut.docs.streaming.Headline")
TimeUnit = java.type("java.util.concurrent.TimeUnit")
PythonAsyncioRuntime = java.type("io.micronaut.context.python.PythonAsyncioRuntime")


@Property(name="spec.name", value="StreamingHeadlineControllerSpec")
@MicronautTest
class AsyncHeadlineClientSpec:
    headlineClient: Annotated[AsyncHeadlineClient, Inject]
    client: Annotated[AsyncStreamingHttpClient, Inject, Client("/")]

    @Test
    def declarativeClient(self):
        result = PythonAsyncioRuntime.toCompletionStage(self.firstHeadline())
        assert result.toCompletableFuture().get(10, TimeUnit.SECONDS).startswith("Latest Headline")

    # tag::declarative[]
    async def firstHeadline(self) -> str:
        headlines = await self.headlineClient.streamHeadlines()  # <1>
        try:
            first = await headlines.next()  # <2>
            return first.orElseThrow().text
        finally:
            headlines.close()  # <3>
        # end::declarative[]

    @Test
    def asyncStreamingClient(self):
        result = PythonAsyncioRuntime.toCompletionStage(self.firstStreamingHeadline())
        assert result.toCompletableFuture().get(10, TimeUnit.SECONDS).startswith("Latest Headline")

    # tag::async[]
    async def firstStreamingHeadline(self) -> str:
        headlines = await self.client.jsonStream(HttpRequest.GET("/streaming/headlines"), HeadlineClass)  # <1>
        try:
            first = await headlines.next()  # <2>
            return first.orElseThrow().text
        finally:
            headlines.close()  # <3>
        # end::async[]
