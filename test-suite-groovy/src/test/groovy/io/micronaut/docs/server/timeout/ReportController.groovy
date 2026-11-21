package io.micronaut.docs.server.timeout

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.RequestTimeout
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

@Requires(property = "spec.name", value = "ReportControllerSpec")
// tag::clazz[]
@Controller("/reports")
@RequestTimeout("500ms") // <1>
@Produces(MediaType.TEXT_PLAIN)
@ExecuteOn(TaskExecutors.BLOCKING)
class ReportController {

    @Get("/summary")
    String summary() { // <2>
        "summary"
    }

    @Get("/export")
    @RequestTimeout("5s") // <3>
    String export() {
        Thread.sleep(1000)
        "export"
    }

    @Get("/archive")
    @RequestTimeout("0") // <4>
    String archive() {
        Thread.sleep(1000)
        "archive"
    }

    @Get("/rebuild")
    String rebuild() { // <5>
        Thread.sleep(1500)
        "rebuilt"
    }
}
// end::clazz[]
