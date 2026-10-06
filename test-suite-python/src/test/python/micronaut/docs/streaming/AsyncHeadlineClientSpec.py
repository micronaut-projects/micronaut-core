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


@Property(name="spec.name", value="StreamingHeadlineControllerSpec")
@MicronautTest
class AsyncHeadlineClientSpec:
    headlineClient: Annotated[AsyncHeadlineClient, Inject]
    client: Annotated[AsyncStreamingHttpClient, Inject, Client("/")]

    @Test
    def declarativeClient(self):
        # tag::declarative[]
        headlines = self.headlineClient.streamHeadlines() \
            .toCompletableFuture().get(10, TimeUnit.SECONDS)  # <1>
        first = headlines.next() \
            .toCompletableFuture().get(10, TimeUnit.SECONDS)  # <2>
        headlines.close()  # <3>
        # end::declarative[]

        assert first.orElseThrow().text.startswith("Latest Headline")

    @Test
    def asyncStreamingClient(self):
        # tag::async[]
        def first_of(headlines):
            return headlines.next() \
                .whenComplete(lambda headline, error: headlines.close())  # <2> <3>

        first = self.client.jsonStream(HttpRequest.GET("/streaming/headlines"), HeadlineClass) \
            .thenCompose(first_of)  # <1>
        # end::async[]

        assert first.toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().text.startswith("Latest Headline")
