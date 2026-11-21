package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.time.Instant;

import static io.micronaut.web.router.builder.RequestCondition.after;
import static io.micronaut.web.router.builder.RequestCondition.cookie;
import static io.micronaut.web.router.builder.RequestCondition.host;
import static io.micronaut.web.router.builder.RequestCondition.peerAddress;
import static io.micronaut.web.router.builder.ValueMatcher.endsWith;
import static io.micronaut.web.router.builder.ValueMatcher.oneOf;
// end::imports[]

@Requires(property = "spec.name", value = "TrafficConditionRoutesTest")
@Singleton
public class TrafficConditionRoutes implements HttpRoutes {
    private static final Instant LAUNCH = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public void routes(HttpRouteBuilder routes) {
        // tag::traffic[]
        routes.GET("/catalog")
            .where(host(endsWith(".partners.example.com"))) // <1>
            .order(-1)
            .handle((request, pathVariables) -> text("partner catalog"));
        routes.GET("/catalog")
            .where(cookie("catalog", oneOf("v2", "v3")).and(after(LAUNCH))) // <2>
            .order(-2)
            .handle((request, pathVariables) -> text("new catalog"));
        routes.GET("/catalog", (request, pathVariables) -> text("catalog"));
        routes.GET("/internal/status")
            .where(peerAddress("127.0.0.0/8", "::1", "10.0.0.0/8")) // <3>
            .handle((request, pathVariables) -> text("internal"));
        // end::traffic[]
    }

    private static HttpResponse<?> text(String text) {
        return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE);
    }
}
