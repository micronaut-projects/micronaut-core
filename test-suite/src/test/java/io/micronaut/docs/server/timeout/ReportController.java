package io.micronaut.docs.server.timeout;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestTimeout;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

@Requires(property = "spec.name", value = "ReportControllerTest")
// tag::clazz[]
@Controller("/reports")
@RequestTimeout("500ms") // <1>
@Produces(MediaType.TEXT_PLAIN)
@ExecuteOn(TaskExecutors.BLOCKING)
public class ReportController {

    @Get("/summary")
    public String summary() { // <2>
        return "summary";
    }

    @Get("/export")
    @RequestTimeout("5s") // <3>
    public String export() throws InterruptedException {
        Thread.sleep(1000);
        return "export";
    }

    @Get("/archive")
    @RequestTimeout("0") // <4>
    public String archive() throws InterruptedException {
        Thread.sleep(1000);
        return "archive";
    }

    @Get("/rebuild")
    public String rebuild() throws InterruptedException { // <5>
        Thread.sleep(1500);
        return "rebuilt";
    }
}
// end::clazz[]
