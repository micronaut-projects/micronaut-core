package io.micronaut.docs.server.gateway;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.PathVariables;
import io.micronaut.http.gateway.GatewayRequestContext;
import io.micronaut.web.router.builder.RequestHandler;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "GatewayDocsTest")
// tag::clazz[]
@Singleton
@Named("ordersFallback")
public class OrdersFallback implements RequestHandler {

    @Override
    public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) {
        String reason = GatewayRequestContext.find(request)
            .flatMap(GatewayRequestContext::failure)
            .map(failure -> failure.getClass().getSimpleName())
            .orElse("none");
        return HttpResponse.status(HttpStatus.OK).body("fallback: " + reason);
    }
}
// end::clazz[]
