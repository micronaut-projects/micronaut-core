from typing import Annotated
from types import SimpleNamespace

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

from .HeadlineClient import HeadlineClient

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
Flux = java.type("reactor.core.publisher.Flux")
HeadlineClass = java.type("micronaut.docs.streaming.Headline")
Mono = java.type("reactor.core.publisher.Mono")
StreamingHttpClient = java.type("io.micronaut.http.client.StreamingHttpClient")
TimeUnit = java.type("java.util.concurrent.TimeUnit")


@Property(name="spec.name", value="StreamingHeadlineControllerSpec")
@MicronautTest
class HeadlineControllerSpec:
    client: Annotated[StreamingHttpClient, Inject, Client("/")]
    headlineClient: Annotated[HeadlineClient, Inject]

    # tag::streamingClient[]
    @Test
    def testClientAnnotationStreaming(self):
        firstHeadline = getattr(Mono, "from")(self.headlineClient.streamHeadlines())  # <2>

        headline = firstHeadline.block()  # <3>

        assert headline is not None
        assert headline.text.startswith("Latest Headline")
    # end::streamingClient[]

    @Test
    @Disabled("GraalPy cannot adapt a Python subscriber object to org.reactivestreams.Subscriber for Flux.subscribe")
    def testStreamingClient(self):
        # tag::streaming[]
        headlineStream = getattr(Flux, "from")(
            self.client.jsonStream(HttpRequest.GET("/streaming/headlines"), HeadlineClass)
        )  # <1>
        future = CompletableFuture()  # <2>

        subscriber = SimpleNamespace(
            onSubscribe=lambda subscription: subscription.request(1),  # <3>
            onNext=lambda headline: future.complete(headline),  # <4>
            onError=lambda throwable: future.completeExceptionally(throwable),  # <5>
            onComplete=lambda: None,  # <6>
        )
        headlineStream.subscribe(subscriber)
        # end::streaming[]

        headline = future.get(3, TimeUnit.SECONDS)
        assert headline.text.startswith("Latest Headline")
