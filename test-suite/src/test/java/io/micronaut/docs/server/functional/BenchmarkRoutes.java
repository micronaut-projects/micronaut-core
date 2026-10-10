package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
// end::imports[]

@Requires(property = "spec.name", value = "BenchmarkRoutesTest")
// tag::clazz[]
@Singleton
public class BenchmarkRoutes implements HttpDirectRoutes {

    private static final byte[] HELLO = "Hello, World!".getBytes(StandardCharsets.US_ASCII); // <1>

    @Introspected
    public record Message(String message) {
    }

    @Override
    public void routes(DirectRouteBuilder routes) {
        routes.GET("/plaintext").respond(direct -> HttpResponse.ok(HELLO) // <2>
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .header(HttpHeaders.SERVER, "Micronaut")); // <3>
        routes.GET("/json").respond(direct -> HttpResponse.ok(new Message("Hello, World!")) // <4>
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.SERVER, "Micronaut"));
    }
}
// end::clazz[]
