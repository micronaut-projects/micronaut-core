from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Singleton
from micronaut.http import HttpResponse, MediaType, ServerHttpRequest
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes

from .Reports import Reports
# end::imports[]


@Requires(property="spec.name", value="ReportRoutesTest")
# tag::clazz[]
@Singleton
class ReportRoutes(HttpRoutes):
    def __init__(self, reports: Reports):
        self.reports = reports

    def routes(self, routes: HttpRouteBuilder) -> None:
        routes.GET("/reports/{id}").handleAsync(self.report)

    def report(self, request, path_variables):
        id = path_variables.getString("id")
        report = self.reports.generate(id)
        if isinstance(request, ServerHttpRequest):
            request.whenAbandoned(lambda: report.cancel(True))  # <1>
            request.whenResponseCompleted(lambda failure: self.reports.completed(id, failure))  # <2>
        return report.thenApply(lambda text: HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE))
# end::clazz[]
