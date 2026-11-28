package io.micronaut.docs.server.timeout;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.time.Duration;
// end::imports[]

@Requires(property = "spec.name", value = "ReportRoutesTest")
// tag::clazz[]
@Singleton
public class ReportRoutes implements HttpRoutes {

    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.path("/fn-reports", group -> {
            group.requestTimeout(Duration.ofSeconds(10)); // <1>
            group.GET("/summary", (request, pathVariables) ->
                HttpResponse.ok("summary").contentType(MediaType.TEXT_PLAIN_TYPE));
        });
    }
}
// end::clazz[]
