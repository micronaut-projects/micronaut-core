from micronaut.context.annotation import Requires
# tag::imports[]
import java
from jakarta.inject import Singleton
from micronaut.http import HttpResponse
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes

# the attribute that names the limiters of a route, a constant of the annotation type
REQUEST_LIMIT = java.type("io.micronaut.http.server.limit.RequestLimit").ATTRIBUTE
# end::imports[]


@Requires(property="spec.name", value="RequestLimitTest")
# tag::clazz[]
@Singleton
class LimitedReportRoutes(HttpRoutes):

    def routes(self, routes: HttpRouteBuilder) -> None:
        routes.GET("/limited-reports/functional/{id}") \
            .attribute(REQUEST_LIMIT, "reports") \
            .handle(lambda request, path_variables: HttpResponse.ok("report " + path_variables.getString("id")))  # <1>
# end::clazz[]
