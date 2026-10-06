from micronaut.context.annotation import Requires
# tag::imports[]
import java
from jakarta.inject import Singleton
from micronaut.http import HttpMethod, HttpResponse, HttpStatus, MediaType
from micronaut.web.router.builder import DirectRouteBuilder, HttpDirectRoutes, RouteCondition, ValueMatcher

EnumSet = java.type("java.util.EnumSet")
List = java.type("java.util.List")
# end::imports[]


@Requires(property="spec.name", value="DirectRoutesTest")
# tag::clazz[]
@Singleton
class DirectRoutes(HttpDirectRoutes):
    def routes(self, routes: DirectRouteBuilder) -> None:
        routes.path("/probe", self.probes)  # <1>
        routes.GET("/robots.txt", HttpResponse.ok("User-agent: *\nDisallow: /private/\n")
                   .contentType(MediaType.TEXT_PLAIN_TYPE))  # <4>
        (routes.route(EnumSet.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE), "/{+path}")  # <5>
            .where(RouteCondition.any(
                RouteCondition.header("User-Agent", ValueMatcher.contains("badbot").ignoringCase()),
                RouteCondition.peerAddress("203.0.113.0/24")))
                .respond(HttpResponse.status(HttpStatus.FORBIDDEN)))  # <6>

    def probes(self, probe: DirectRouteBuilder) -> None:
        probe.GET("/live", HttpResponse.ok("UP"))  # <2>
        (probe.GET("/{component}")  # <3>
            .constrain("component", List.of("db", "cache"))
            .respond(lambda direct:
                   direct.responses().ok(direct.pathVariables().getString("component") + " UP")))
# end::clazz[]
