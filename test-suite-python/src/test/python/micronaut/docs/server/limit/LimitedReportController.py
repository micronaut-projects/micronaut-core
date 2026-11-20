from micronaut.context.annotation import Requires
# tag::imports[]
from micronaut.http.annotation import Controller, Get
from micronaut.http.server.limit import RequestLimit
# end::imports[]


@Requires(property="spec.name", value="RequestLimitTest")
# tag::clazz[]
@Controller("/limited-reports")
class LimitedReportController:

    @RequestLimit("reports")  # <1>
    @Get("/{id}")
    def report(self, id: str) -> str:
        return "report " + id
# end::clazz[]
