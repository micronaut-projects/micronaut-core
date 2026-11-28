package io.micronaut.docs.server.timeout;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestTimeout;
import io.micronaut.http.server.RequestDeadline;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

@Requires(property = "spec.name", value = "ReportControllerTest")
@Controller("/index")
@Produces(MediaType.TEXT_PLAIN)
public class IndexController {

    // tag::cooperate[]
    @Get("/rebuild")
    @RequestTimeout("500ms")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public String rebuild(HttpRequest<?> request) throws InterruptedException {
        RequestDeadline deadline = RequestDeadline.find(request).orElse(null); // <1>
        for (int step = 0; step < 100; step++) {
            if (deadline != null && deadline.isExpired()) { // <2>
                return "stopped";
            }
            Thread.sleep(20); // a step of the work
        }
        return "rebuilt";
    }
    // end::cooperate[]
}
