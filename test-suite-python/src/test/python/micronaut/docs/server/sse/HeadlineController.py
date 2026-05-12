import java

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get
from micronaut.http.sse import Event
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn

from .Headline import Headline

Flux = java.type("reactor.core.publisher.Flux")
Publisher = java.type("org.reactivestreams.Publisher")
# end::imports[]


@Requires(property="spec.name", value="HeadlineControllerSpec")
# tag::class[]
@Controller("/headlines")
class HeadlineController:

    @ExecuteOn(TaskExecutors.IO)
    @Get(produces=MediaType.TEXT_EVENT_STREAM)
    def index(self) -> Publisher:  # <1>
        versions = ["1.0", "2.0"]  # <2>

        def generator(i, emitter):
            if i < len(versions):
                emitter.next(  # <4>
                    Event.of(
                        Headline("Micronaut " + versions[i] + " Released", "Come and get it")
                    )
                )
            else:
                emitter.complete()  # <5>
            return i + 1

        return Flux.generate(lambda: 0, generator)  # <3>
# end::class[]
