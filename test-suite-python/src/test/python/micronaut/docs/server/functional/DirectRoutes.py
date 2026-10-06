from micronaut.context.annotation import Requires
# tag::imports[]
import java
from jakarta.inject import Singleton
from micronaut.http import HttpHeaders, HttpMethod, HttpResponse, HttpStatus, MediaType
from micronaut.web.router.builder import DirectRouteBuilder, HttpDirectRoutes, RouteCondition, ValueMatcher

EnumSet = java.type("java.util.EnumSet")
List = java.type("java.util.List")
# end::imports[]


# tag::clazz[]
VERSION = '"1.0.0"'


# end::clazz[]
@Requires(property="spec.name", value="DirectRoutesTest")
# tag::clazz[]
@Singleton
class DirectRoutes(HttpDirectRoutes):
    def routes(self, routes: DirectRouteBuilder) -> None:
        routes.path("/probe", self.probes)  # <1>
        routes.GET("/robots.txt", HttpResponse.ok("User-agent: *\nDisallow: /private/\n")
                   .contentType(MediaType.TEXT_PLAIN_TYPE))  # <4>
        routes.GET("/version").respond(lambda direct:
                   HttpResponse.notModified() if direct.request().header(HttpHeaders.IF_NONE_MATCH) == VERSION  # <5>
                   else HttpResponse.ok("1.0.0").header(HttpHeaders.ETAG, VERSION))
        (routes.route(EnumSet.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE), "/{+path}")  # <6>
            .where(RouteCondition.any(
                RouteCondition.header("User-Agent", ValueMatcher.contains("badbot").ignoringCase()),
                RouteCondition.peerAddress("203.0.113.0/24")))
                .respond(HttpResponse.status(HttpStatus.FORBIDDEN)))  # <7>

    def probes(self, probe: DirectRouteBuilder) -> None:
        probe.GET("/live", HttpResponse.ok("UP"))  # <2>
        (probe.GET("/{component}")  # <3>
            .constrain("component", List.of("db", "cache"))
            .respond(lambda direct:
                   HttpResponse.ok(direct.pathVariables().getString("component") + " UP")))
# end::clazz[]
