import java
from micronaut.context.annotation import Requires

# tag::imports[]
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get

from .Headline import Headline

Duration = java.type("java.time.Duration")
Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")
ZonedDateTime = java.type("java.time.ZonedDateTime")
# end::imports[]


@Requires(property="spec.name", value="StreamingHeadlineControllerSpec")
@Controller("/streaming")
class HeadlineController:

    # tag::streaming[]
    @Get(value="/headlines", processes=MediaType.APPLICATION_JSON_STREAM)  # <1>
    def streamHeadlines(self) -> Publisher[Headline]:
        def build_headline():
            return Headline("Latest Headline at " + str(ZonedDateTime.now()))

        return (
            Mono.fromCallable(build_headline)  # <2>
            .repeat(100)  # <3>
            .delayElements(Duration.ofSeconds(1))  # <4>
        )
    # end::streaming[]
