from typing import Annotated

import java

from micronaut.context.annotation import Requires
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Body, Controller, Post
from micronaut.http.sse import Event

Flux = java.type("reactor.core.publisher.Flux")


@Requires(property="spec.name", value="AsyncSseClientSpec")
@Controller("/mcp")
class McpController:

    @Post(produces=MediaType.TEXT_EVENT_STREAM)
    def message(self, message: Annotated[str, Body]) -> HttpResponse:
        return HttpResponse.ok(Flux.just(Event.of("progress"), Event.of("done"))) \
            .header("Mcp-Session-Id", "abc-123")
