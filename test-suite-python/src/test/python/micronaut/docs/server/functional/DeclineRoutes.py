from jakarta.inject import Singleton
from micronaut.context.annotation import Requires
from micronaut.http import HttpResponse, MediaType
from micronaut.web.router.builder import DirectRouteBuilder, HttpDirectRoutes, HttpRouteBuilder, HttpRoutes

CACHED = {"logo.png", "style.css"}


@Requires(property="spec.name", value="DirectRoutesTest")
# tag::clazz[]
@Singleton
class CachedAssets(HttpDirectRoutes):
    def routes(self, routes: DirectRouteBuilder) -> None:
        routes.GET("/assets/{name}").respond(lambda direct:
                   direct.responses().ok("cached " + direct.pathVariables().getString("name"))
                   if direct.pathVariables().getString("name") in CACHED else None)  # <1>


# end::clazz[]
@Requires(property="spec.name", value="DirectRoutesTest")
# tag::clazz[]
@Singleton
class RenderedAssets(HttpRoutes):
    def routes(self, routes: HttpRouteBuilder) -> None:
        routes.GET("/assets/{name}", lambda request, path_variables:
                   HttpResponse.ok("rendered " + path_variables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE))  # <2>
# end::clazz[]
