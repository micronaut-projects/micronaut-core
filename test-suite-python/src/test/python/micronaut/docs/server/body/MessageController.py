from typing import Annotated

import java
# tag::imports[]
from jakarta.validation.constraints import Size
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Body, Controller, Post
# end::imports[]
# tag::importsreactive[]
from micronaut.core.async_.annotation import SingleResult
# end::importsreactive[]

Flux = java.type("reactor.core.publisher.Flux")
Publisher = java.type("org.reactivestreams.Publisher")


# tag::class[]
@Controller("/receive")
class MessageController:
# end::class[]

    # tag::echo[]
    @Post(value="/echo", consumes=MediaType.TEXT_PLAIN)  # <1>
    def echo(self, text: Annotated[str, Size(max=1024), Body]) -> str:  # <2>
        return text  # <3>
    # end::echo[]

    # tag::echoReactive[]
    @Post(value="/echo-publisher", consumes=MediaType.TEXT_PLAIN)  # <1>
    @SingleResult
    def echoFlow(self, text: Annotated[Publisher, Body]) -> Publisher:  # <2>
        return Flux.from_(text).collect(lambda: [], lambda buffer, value: buffer.append(value)).map(  # <3>
            lambda buffer: HttpResponse.ok("".join(buffer))
        )
    # end::echoReactive[]
# tag::endclass[]
# end::endclass[]
