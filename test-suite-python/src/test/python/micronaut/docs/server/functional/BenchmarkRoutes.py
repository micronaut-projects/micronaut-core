from micronaut.context.annotation import Requires
# tag::imports[]
import time
from dataclasses import dataclass

import java
from jakarta.inject import Singleton
from micronaut.core.annotation import Introspected
from micronaut.http import HttpHeaders, MediaType
from micronaut.web.router.direct import DirectRouteBuilder, HttpDirectRoutes

StandardCharsets = java.type("java.nio.charset.StandardCharsets")
Instant = java.type("java.time.Instant")
ZoneOffset = java.type("java.time.ZoneOffset")
DateTimeFormatter = java.type("java.time.format.DateTimeFormatter")
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
    def __init__(self):
        self.cached_date = (-1, "")  # <2>

    def date(self) -> str:
        """The Date header value, formatted at most once per second."""
        second = int(time.time())
        cached = self.cached_date
        if cached[0] != second:
            cached = (second, DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochSecond(second).atZone(ZoneOffset.UTC)))
            self.cached_date = cached
        return cached[1]

    def routes(self, routes: DirectRouteBuilder) -> None:
        routes.GET("/plaintext").respond(lambda direct:  # <3>
                   direct.responses().ok(HELLO)
                   .contentType(MediaType.TEXT_PLAIN_TYPE)
                   .header(HttpHeaders.SERVER, "Micronaut")
                   .header(HttpHeaders.DATE, self.date()))
        routes.GET("/json").respond(lambda direct:  # <4>
                   direct.responses().ok(Message("Hello, World!"))
                   .contentType(MediaType.APPLICATION_JSON_TYPE)
                   .header(HttpHeaders.SERVER, "Micronaut")
                   .header(HttpHeaders.DATE, self.date()))
# end::clazz[]
