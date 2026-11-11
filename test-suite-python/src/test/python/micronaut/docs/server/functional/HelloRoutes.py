from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Singleton
from micronaut.http import HttpResponse, MediaType
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes
# end::imports[]


@Requires(property="spec.name", value="HelloRoutesTest")
# tag::clazz[]
@Singleton
class HelloRoutes(HttpRoutes):
    def routes(self, routes: HttpRouteBuilder) -> None:
        routes.GET("/hello/{name}", lambda request, path_variables:
                   HttpResponse.ok("Hello " + path_variables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE))
# end::clazz[]
