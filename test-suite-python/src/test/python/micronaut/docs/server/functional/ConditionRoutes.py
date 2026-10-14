from micronaut.context.annotation import Requires
# tag::imports[]
from typing import Annotated

import java
from jakarta.inject import Singleton
from micronaut.context.annotation import Value
from micronaut.http import HttpResponse, HttpStatus, MediaType
from micronaut.web.router import RouteAttributes
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes, RouteCondition, ValueMatcher

Instant = java.type("java.time.Instant")
Long = java.type("java.lang.Long")
Set = java.type("java.util.Set")
String = java.type("java.lang.String")
# end::imports[]

SHOPS = Set.of("north", "south")


def text(text: str) -> HttpResponse:
    return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE)


@Requires(property="spec.name", value="ConditionRoutesTest")
@Singleton
class ConditionRoutes(HttpRoutes):

    def __init__(self, management_port: Annotated[int, Value("${management.port}")]):
        self.management_port = management_port

    def routes(self, routes: HttpRouteBuilder) -> None:
        # tag::where[]
        (routes.GET("/search")
            .where(RouteCondition.any(  # <1>
                RouteCondition.header("X-Beta"),
                RouteCondition.query("beta", "true")))
            .order(-1)  # <2>
            .handle(lambda request, path_variables: text("beta search")))
        routes.GET("/search", lambda request, path_variables: text("search"))  # <3>
        # end::where[]
        # tag::constrain[]
        def shop_routes(shop):
            shop.constrain("shop", SHOPS)  # <1>
            shop.GET("/stock", lambda request, path_variables: text("stock of " + path_variables.getString("shop")))

        routes.path("/shops/{shop}", shop_routes)
        (routes.GET("/items/{id}")
            .constrain("id", Long, lambda id: id > 0)  # <2>
            .order(-1)
            .handle(lambda request, path_variables: text(f"item {path_variables.getLong('id')}")))
        routes.GET("/items/{name}", lambda request, path_variables: text("item named " + path_variables.getString("name")))  # <3>
        # end::constrain[]
        # tag::matchers[]
        (routes.GET("/downloads/{file}")
            .where(RouteCondition.header("X-Channel", ValueMatcher.oneOf("beta", "canary").ignoringCase())  # <1>
                .or_(RouteCondition.cookie("channel", ValueMatcher.equalTo("beta"))))
            .where(RouteCondition.peerAddress("127.0.0.0/8", "::1"))  # <2>
            .where(RouteCondition.after(Instant.parse("2026-01-01T00:00:00Z")))  # <3>
            .constrain("file", ValueMatcher.endsWith(".zip"))  # <4>
            .handle(lambda request, path_variables: text("download " + path_variables.getString("file"))))
        # end::matchers[]
        # tag::attributes[]
        def report_routes(reports):
            reports.attribute("role", "auditor")  # <1>

            def check_role(request):
                role = (RouteAttributes.getRouteInfo(request)  # <2>
                        .flatMap(lambda route: route.getAttribute("role", String))
                        .orElseThrow())
                return None if role == request.getHeaders().get("X-Role") else HttpResponse.status(HttpStatus.FORBIDDEN)

            reports.beforeReplacing(check_role)
            reports.GET("/daily", lambda request, path_variables: text("daily report"))
            (reports.GET("/salaries")
                .attribute("role", "admin")  # <3>
                .handle(lambda request, path_variables: text("salaries")))

        routes.path("/reports", report_routes)
        # end::attributes[]
        # tag::port[]
        def management_routes(management):
            management.port(self.management_port)  # <1>
            management.GET("/health", lambda request, path_variables: text("UP"))

        routes.path("/management", management_routes)
        # end::port[]
