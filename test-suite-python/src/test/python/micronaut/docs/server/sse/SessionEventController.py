from typing import Annotated

import java
from micronaut.context.annotation import Requires
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Body, Controller, Post
from micronaut.http.sse import Event

Flux = java.type("reactor.core.publisher.Flux")
Publisher = java.type("org.reactivestreams.Publisher")


@Requires(property="spec.name", value="SessionEventControllerSpec")
@Controller("/session-events")
class SessionEventController:

    @Post(produces=MediaType.TEXT_EVENT_STREAM)
    def message(self, message: Annotated[str, Body]) -> HttpResponse[Publisher[Event[str]]]:
        return HttpResponse.ok(
            Flux.just(Event.of("progress"), Event.of("done"))
        ).header("Mcp-Session-Id", "abc-123")
