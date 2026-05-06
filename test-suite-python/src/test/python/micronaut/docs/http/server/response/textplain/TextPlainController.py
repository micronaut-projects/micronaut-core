import java
from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get, Produces

from .Person import Person

BigDecimal = java.type("java.math.BigDecimal")
Calendar = java.type("java.util.Calendar")
Flux = java.type("reactor.core.publisher.Flux")
Long = java.type("java.lang.Long")
Mono = java.type("reactor.core.publisher.Mono")
Publisher = java.type("org.reactivestreams.Publisher")


@Requires(property="spec.name", value="TextPlainControllerTest")
# tag::classopening[]
@Controller("/txt")
class TextPlainController:
# end::classopening[]
    @Get("/boolean")
    @Produces(MediaType.TEXT_PLAIN)  # <1>
    def bool(self) -> str:
        return "true"  # <2>

    @Get("/boolean/mono")
    @Produces(MediaType.TEXT_PLAIN)  # <1>
    # TODO: Re-enable @SingleResult when Python can import io.micronaut.core.async.annotation.SingleResult.
    # @SingleResult
    def monoBool(self) -> Publisher:
        return Mono.just("true")  # <2>

    @Get("/boolean/flux")
    @Produces(MediaType.TEXT_PLAIN)
    # TODO: Re-enable @SingleResult when Python can import io.micronaut.core.async.annotation.SingleResult.
    # @SingleResult
    def fluxBool(self) -> Publisher:
        return Flux.just("true")

    @Get("/bigdecimal")
    @Produces(MediaType.TEXT_PLAIN)  # <1>
    def bigDecimal(self) -> str:
        return BigDecimal.valueOf(Long.MAX_VALUE).toString()  # <2>

# tag::method[]
    @Get("/date")
    @Produces(MediaType.TEXT_PLAIN)  # <1>
    def date(self) -> str:
        return Calendar.Builder().setDate(2023, 7, 4).build().toString()  # <2>
# end::method[]
    @Get("/person")
    @Produces(MediaType.TEXT_PLAIN)  # <1>
    def person(self) -> str:
        return str(Person("Dean Wette", 65))  # <2>
# tag::classclosing[]
# end::classclosing[]
