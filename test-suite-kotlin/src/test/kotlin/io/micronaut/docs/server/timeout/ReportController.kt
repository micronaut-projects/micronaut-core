package io.micronaut.docs.server.timeout

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.RequestTimeout
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

@Requires(property = "spec.name", value = "ReportControllerTest")
// tag::clazz[]
@Controller("/reports")
@RequestTimeout("500ms") // <1>
@Produces(MediaType.TEXT_PLAIN)
@ExecuteOn(TaskExecutors.BLOCKING)
class ReportController {

    @Get("/summary")
    fun summary(): String { // <2>
        return "summary"
    }

    @Get("/export")
    @RequestTimeout("5s") // <3>
    fun export(): String {
        Thread.sleep(1000)
        return "export"
    }

    @Get("/archive")
    @RequestTimeout("0") // <4>
    fun archive(): String {
        Thread.sleep(1000)
        return "archive"
    }

    @Get("/rebuild")
    fun rebuild(): String { // <5>
        Thread.sleep(1500)
        return "rebuilt"
    }
}
// end::clazz[]
