from micronaut.context.annotation import Requires
# tag::imports[]
import itertools

import java
from jakarta.inject import Singleton
from micronaut.http import HttpMethod, HttpResponse, HttpStatus, MediaType
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes

URI = java.type("java.net.URI")
# end::imports[]


@Requires(property="spec.name", value="RespondRoutesTest")
# tag::clazz[]
@Singleton
class RespondRoutes(HttpRoutes):

    def __init__(self):
        self.visits = itertools.count(1)

    def routes(self, routes: HttpRouteBuilder) -> None:
        (routes.GET("/ping")  # <1>
            .after(lambda request, response: response.header("Cache-Control", "max-age=60")).and_()  # <2>
            .respond(HttpResponse.ok("pong").contentType(MediaType.TEXT_PLAIN_TYPE)))
        routes.GET("/old-ping").respond(HttpResponse.permanentRedirect(URI.create("/ping")))  # <3>
        routes.GET("/visits").respond(lambda:
                       HttpResponse.ok(f"visit {next(self.visits)}").contentType(MediaType.TEXT_PLAIN_TYPE))  # <4>
        routes.GET("/greetings/{name}").respond(lambda path_variables:
                       HttpResponse.ok("Hello " + path_variables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE))  # <5>
        routes.POST("/legacy/webhook").respond(HttpResponse.status(HttpStatus.GONE))  # <6>
# end::clazz[]
