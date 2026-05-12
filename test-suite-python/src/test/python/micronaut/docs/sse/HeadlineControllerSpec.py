from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .HeadlineClient import HeadlineClient

Mono = java.type("reactor.core.publisher.Mono")


@Property(name="spec.name", value="SseHeadlineControllerSpec")
@MicronautTest
class HeadlineControllerSpec:
    headlineClient: Annotated[HeadlineClient, Inject]

    # tag::streamingClient[]
    @Test
    def testClientAnnotationStreaming(self):
        headline = Mono.from_(self.headlineClient.streamHeadlines()).block()

        assert headline is not None
        assert headline.getData().text.startswith("Latest Headline")
    # end::streamingClient[]
