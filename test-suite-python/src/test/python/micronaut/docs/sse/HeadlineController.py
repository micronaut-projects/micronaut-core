import java

from micronaut.context.annotation import Requires
from micronaut.docs.streaming.Headline import Headline
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get
from micronaut.http.sse import Event

Duration = java.type("java.time.Duration")
Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")


@Requires(property="spec.name", value="SseHeadlineControllerSpec")
@Controller("/streaming/sse")
class HeadlineController:

    # tag::streaming[]
    @Get(value="/headlines", processes=MediaType.TEXT_EVENT_STREAM)  # <1>
    def streamHeadlines(self) -> Publisher[Event[Headline]]:
        def build_event():
            headline = Headline("Latest Headline")
            return Event.of(headline)

        return (
            Mono.fromCallable(build_event)  # <2>
            .repeat(100)  # <3>
            .delayElements(Duration.ofSeconds(1))  # <4>
        )
    # end::streaming[]
