import java
from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Singleton
from micronaut.http import HttpResponse, MediaType
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes

Duration = java.type("java.time.Duration")
# end::imports[]


@Requires(property="spec.name", value="ReportRoutesTest")
# tag::clazz[]
@Singleton
class ReportRoutes(HttpRoutes):
    def routes(self, routes: HttpRouteBuilder) -> None:
        def group_routes(group):
            group.requestTimeout(Duration.ofSeconds(10))  # <1>
            group.GET("/summary", lambda request, path_variables:
                      HttpResponse.ok("summary").contentType(MediaType.TEXT_PLAIN_TYPE))

        routes.path("/fn-reports", group_routes)
# end::clazz[]
