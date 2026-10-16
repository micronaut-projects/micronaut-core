from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Singleton
from micronaut.http import HttpHeaders, HttpResponse, MediaType
from micronaut.web.router.builder import DirectRouteBuilder, HttpDirectRoutes, RouteCondition, ValueMatcher
# end::imports[]

# tag::clazz[]
VERSION = '"2026.10"'


# end::clazz[]
@Requires(property="spec.name", value="NotModifiedRoutesTest")
# tag::clazz[]
@Singleton
class NotModifiedRoutes(HttpDirectRoutes):
    def routes(self, routes: DirectRouteBuilder) -> None:
        (routes.GET("/release")
         .where(RouteCondition.header(HttpHeaders.IF_NONE_MATCH, ValueMatcher.entityTag(VERSION)))  # <1>
         .order(-1)  # <2>
         .respond(HttpResponse.notModified().header(HttpHeaders.ETAG, VERSION)))
        routes.GET("/release", HttpResponse.ok("release 2026.10")  # <3>
                   .contentType(MediaType.TEXT_PLAIN_TYPE)
                   .header(HttpHeaders.ETAG, VERSION))
# end::clazz[]
