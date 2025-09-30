package io.micronaut.http.server.netty.errors.handler;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.slf4j.MDC;

@Requires(property = "spec.name", value = "PropagatedContextExceptionHandlerTest")
@Controller("/blocking")
@ExecuteOn(TaskExecutors.BLOCKING)
public class BlockingController {

    @Get
    public String get() {
        throw new RuntimeException(MDC.get("trace"));
    }
}
