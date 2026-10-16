from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Singleton
from micronaut.http import HttpHeaders, HttpResponse, MediaType
from micronaut.web.router.builder import DirectRouteBuilder, HttpDirectRoutes
# end::imports[]

# tag::clazz[]
VERSION = '"2026.10"'


# end::clazz[]
@Requires(property="spec.name", value="PreparedRoutesTest")
# tag::clazz[]
@Singleton
class PreparedRoutes(HttpDirectRoutes):
    def routes(self, routes: DirectRouteBuilder) -> None:
        current = routes.prepare(HttpResponse.ok("release 2026.10")  # <1>
                                 .contentType(MediaType.TEXT_PLAIN_TYPE)
                                 .header(HttpHeaders.ETAG, VERSION))
        not_modified = routes.prepare(HttpResponse.notModified()
                                      .header(HttpHeaders.ETAG, VERSION))
        routes.GET("/release").respond(lambda direct:
                                       not_modified if direct.request().header(HttpHeaders.IF_NONE_MATCH) == VERSION
                                       else current)  # <2>
# end::clazz[]
