from micronaut.context.annotation import Requires
# tag::imports[]
from dataclasses import dataclass

import java
from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected
from micronaut.http import HttpHeaders, MediaType
from micronaut.web.router.builder import DirectRouteBuilder, HttpDirectRoutes

StandardCharsets = java.type("java.nio.charset.StandardCharsets")
# end::imports[]

# tag::clazz[]
HELLO = StandardCharsets.US_ASCII.encode("Hello, World!")  # <1>


@Introspected
@dataclass
class Message:
    message: str


# end::clazz[]
@Requires(property="spec.name", value="BenchmarkRoutesTest")
# tag::clazz[]
@Singleton
class BenchmarkRoutes(HttpDirectRoutes):
    def routes(self, routes: DirectRouteBuilder) -> None:
        routes.GET("/plaintext").respond(lambda direct:  # <2>
                   direct.responses().ok(HELLO)
                   .contentType(MediaType.TEXT_PLAIN_TYPE)
                   .header(HttpHeaders.SERVER, "Micronaut"))  # <3>
        routes.GET("/json").respond(lambda direct:  # <4>
                   direct.responses().ok(Message("Hello, World!"))
                   .contentType(MediaType.APPLICATION_JSON_TYPE)
                   .header(HttpHeaders.SERVER, "Micronaut"))
# end::clazz[]
