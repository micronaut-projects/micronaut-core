import java
from micronaut.context.annotation import Requires
from micronaut.http import HttpRequest, MediaType
from micronaut.http.annotation import Controller, Get, Produces, RequestTimeout
from micronaut.scheduling.annotation import ExecuteOn

RequestDeadline = java.type("io.micronaut.http.server.RequestDeadline")
Thread = java.type("java.lang.Thread")


@Requires(property="spec.name", value="ReportControllerTest")
@Controller("/index")
@Produces(MediaType.TEXT_PLAIN)
class IndexController:

    # tag::cooperate[]
    @Get("/rebuild")
    @RequestTimeout("500ms")
    @ExecuteOn("blocking")
    def rebuild(self, request: HttpRequest) -> str:
        deadline = RequestDeadline.find(request).orElse(None)  # <1>
        for step in range(100):
            if deadline is not None and deadline.isExpired():  # <2>
                return "stopped"
            Thread.sleep(20)  # a step of the work
        return "rebuilt"
    # end::cooperate[]
