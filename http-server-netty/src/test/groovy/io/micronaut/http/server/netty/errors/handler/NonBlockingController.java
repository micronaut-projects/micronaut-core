package io.micronaut.http.server.netty.errors.handler;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import org.slf4j.MDC;

@Requires(property = "spec.name", value = "PropagatedContextExceptionHandlerTest")
@Controller("/non-blocking")
public class NonBlockingController {

    @Get
    public String get() {
        throw new RuntimeException(MDC.get("trace"));
    }
}
