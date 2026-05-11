from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .HeadlineClient import HeadlineClient

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
ClassLoader = java.type("java.lang.ClassLoader")
Flux = java.type("reactor.core.publisher.Flux")
HeadlineClass = java.type("micronaut.docs.streaming.Headline")
Mono = java.type("reactor.core.publisher.Mono")
Proxy = java.type("java.lang.reflect.Proxy")
Subscriber = java.type("org.reactivestreams.Subscriber")
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
    def testStreamingClient(self):
        # tag::streaming[]
        headlineStream = getattr(Flux, "from")(
            self.client.jsonStream(HttpRequest.GET("/streaming/headlines"), HeadlineClass)
        )  # <1>
        future = CompletableFuture()  # <2>
        subscription = [None]

        def handle_signal(proxy, method, args):
            if method.getName() == "onSubscribe":
                subscription[0] = args[0]
                subscription[0].request(1)  # <3>
            elif method.getName() == "onNext":
                if future.complete(args[0]) and subscription[0] is not None:  # <4>
                    subscription[0].cancel()
            elif method.getName() == "onError":
                future.completeExceptionally(args[0])  # <5>
            elif method.getName() == "onComplete":
                pass  # <6>

        subscriber = Proxy.newProxyInstance(
            ClassLoader.getSystemClassLoader(),
            [Subscriber],
            handle_signal
        )
        headlineStream.subscribe(subscriber)
        # end::streaming[]

        headline = future.get(3, TimeUnit.SECONDS)
        assert headline.text.startswith("Latest Headline")
