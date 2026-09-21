from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, MediaType
from micronaut.http.client import HttpClient, StreamingHttpClient
from micronaut.http.client.annotation import Client
from micronaut.runtime.server import EmbeddedServer
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test

Argument = java.type("io.micronaut.core.type.Argument")
Duration = java.type("java.time.Duration")
Flux = java.type("reactor.core.publisher.Flux")
StreamProbe = java.type("micronaut.docs.asyncio.streaming.StreamProbe")
TickClass = java.type("micronaut.docs.asyncio.streaming.Tick")


@Property(name="spec.name", value="PythonStreamingSpec")
@Property(name="micronaut.netty.event-loops.default.num-threads", value="1")
@Property(name="micronaut.netty.event-loops.client.num-threads", value="1")
@Property(name="micronaut.http.client.event-loop-group", value="client")
@Property(name="micronaut.http.client.read-timeout", value="30s")
@MicronautTest
class PythonStreamingSpec:
    client: Annotated[HttpClient, Inject, Client("/")]
    streaming_client: Annotated[StreamingHttpClient, Inject, Client("/")]
    server: Annotated[EmbeddedServer, Inject]

    @BeforeEach
    def reset(self):
        StreamProbe.reset()

    def _json_stream(self, path):
        return Flux.from_(self.streaming_client.jsonStream(HttpRequest.GET(path), TickClass))

    @Test
    def explicitPublisherStreamsJsonItemsInOrder(self):
        ticks = self._json_stream("/async-streams/ticks").collectList().block(Duration.ofSeconds(30))

        assert [tick.label for tick in ticks] == ["tick-0", "tick-1", "tick-2"], ticks
        assert [tick.index for tick in ticks] == [0, 1, 2]

    @Test
    def asyncGeneratorRouteStreamsServerSentEvents(self):
        events = Flux.from_(
            self.streaming_client.eventStream(HttpRequest.GET("/async-streams/events"), Argument.of(TickClass))
        ).collectList().block(Duration.ofSeconds(30))

        assert [event.getData().label for event in events] == ["event-0", "event-1", "event-2"]

    @Test
    def asyncGeneratorRouteStreamsPlainValues(self):
        values = Flux.from_(
            self.streaming_client.jsonStream(HttpRequest.GET("/async-streams/strings"), java.type("java.lang.String"))
        ).collectList().block(Duration.ofSeconds(30))

        assert list(values) == ["a", "b", "c"], values

    @Test
    def clientObservesTheFirstItemBeforeTheLastIsGenerated(self):
        first = self._json_stream("/async-streams/gated").next().block(Duration.ofSeconds(30))

        assert first.label == "before-gate"
        # the generator only reaches the gate once the writer asks for the second item; either way the
        # client held the first item before the second was produced
        assert StreamProbe.awaitGate(10), "the generator did not pause on its gate after the first item was delivered"
        self.client.toBlocking().retrieve("/async-streams/release")

    @Test
    def disconnectingTheClientClosesTheGenerator(self):
        received = StreamProbe.readAndDisconnect(self.server.getPort(), "/async-streams/endless", 200)

        assert "endless-0" in received, received
        assert StreamProbe.awaitFinished("endless", 1, 10), "the generator's finally block did not run after the client disconnected"

    @Test
    def repeatedConnectAndDisconnectLeavesNoGeneratorBehind(self):
        for _ in range(20):
            StreamProbe.readAndDisconnect(self.server.getPort(), "/async-streams/endless", 100)

        assert StreamProbe.awaitFinished("endless", 20, 20), f"started={StreamProbe.startedCount('endless')} finished={StreamProbe.finishedCount('endless')}"
        assert StreamProbe.startedCount("endless") == 20

    @Test
    def asyncControllerConsumesAStreamingClientAndStopsEarly(self):
        labels = self.client.toBlocking().retrieve("/async-streams/consume")

        assert labels == "counted-0,counted-1,counted-2", labels
        assert StreamProbe.awaitFinished("counted", 1, 10), "the upstream generator did not finish"

    @Test
    def aFailingGeneratorFailsTheStream(self):
        failure = StreamProbe.blockingFailure(self.streaming_client.jsonStream(HttpRequest.GET("/async-streams/failing"), TickClass))

        assert failure is not None, "the stream completed normally"
