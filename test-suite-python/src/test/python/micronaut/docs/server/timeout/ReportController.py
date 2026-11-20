import java
from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get, Produces, RequestTimeout
from micronaut.scheduling.annotation import ExecuteOn

Thread = java.type("java.lang.Thread")


@Requires(property="spec.name", value="ReportControllerTest")
# tag::clazz[]
@Controller("/reports")
@RequestTimeout("500ms")  # <1>
@Produces(MediaType.TEXT_PLAIN)
@ExecuteOn("blocking")
class ReportController:

    @Get("/summary")
    def summary(self) -> str:  # <2>
        return "summary"

    @Get("/export")
    @RequestTimeout("5s")  # <3>
    def export(self) -> str:
        Thread.sleep(1000)
        return "export"

    @Get("/archive")
    @RequestTimeout("0")  # <4>
    def archive(self) -> str:
        Thread.sleep(1000)
        return "archive"

    @Get("/rebuild")
    def rebuild(self) -> str:  # <5>
        Thread.sleep(1500)
        return "rebuilt"
# end::clazz[]
