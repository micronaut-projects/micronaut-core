package io.micronaut.http.server.netty.errors.handler;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.slf4j.MDC;

@Requires(property = "spec.name", value = "PropagatedContextExceptionHandlerTest")
@Produces
@Singleton
public class DefaultExceptionHandler implements ExceptionHandler<Exception, HttpResponse<String>> {
    @Override
    public HttpResponse<String> handle(HttpRequest request, Exception e) {
        String json = """
            {
             "controllerTraceId": "%s",
             "exceptionHandlerTraceId": "%s"
            }
            """.formatted(e.getMessage(), MDC.get("trace"));
        return HttpResponse.serverError(json);
    }
}
