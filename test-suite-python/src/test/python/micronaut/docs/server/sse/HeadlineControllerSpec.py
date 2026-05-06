from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest
from micronaut.http.client.annotation import Client
from micronaut.http.client.sse import SseClient
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Flux = java.type("reactor.core.publisher.Flux")
HeadlineClass = java.type("micronaut.docs.server.sse.Headline")


@Property(name="spec.name", value="HeadlineControllerSpec")
@MicronautTest
class HeadlineControllerSpec:
    client: Annotated[SseClient, Inject, Client("/")]

    @Test
    def testConsumeEventStreamObject(self):
        events = getattr(Flux, "from")(
            self.client.eventStream(HttpRequest.GET("/headlines"), HeadlineClass)
        ).collectList().block()

        assert events.size() == 2, "events size: " + str(events.size())
        assert events.get(0).getData().getTitle() == "Micronaut 1.0 Released"
        assert events.get(0).getData().getDescription() == "Come and get it"
