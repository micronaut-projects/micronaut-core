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
        (routes.respond("/ping", HttpResponse.ok("pong").contentType(MediaType.TEXT_PLAIN_TYPE))  # <1>
            .after(lambda request, response: response.header("Cache-Control", "max-age=60")))  # <2>
        routes.respond("/old-ping", HttpResponse.permanentRedirect(URI.create("/ping")))  # <3>
        routes.respond("/visits", lambda:
                       HttpResponse.ok(f"visit {next(self.visits)}").contentType(MediaType.TEXT_PLAIN_TYPE))  # <4>
        routes.respond("/greetings/{name}", lambda path_variables:
                       HttpResponse.ok("Hello " + path_variables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE))  # <5>
        routes.respond(HttpMethod.POST, "/legacy/webhook", HttpResponse.status(HttpStatus.GONE))  # <6>
# end::clazz[]
