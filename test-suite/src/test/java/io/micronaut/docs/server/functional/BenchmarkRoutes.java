package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.direct.DirectRouteBuilder;
import io.micronaut.web.router.direct.HttpDirectRoutes;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
// end::imports[]

@Requires(property = "spec.name", value = "BenchmarkRoutesTest")
// tag::clazz[]
@Singleton
public class BenchmarkRoutes implements HttpDirectRoutes {

    private static final byte[] HELLO = "Hello, World!".getBytes(StandardCharsets.US_ASCII); // <1>

    private volatile HttpDate date = new HttpDate(-1, ""); // <2>

    @Introspected
    public record Message(String message) {
    }

    private record HttpDate(long second, String text) {
    }

    /**
     * @return The Date header value, formatted at most once per second
     */
    String date() {
        long second = System.currentTimeMillis() / 1000;
        HttpDate current = date;
        if (current.second() != second) {
            current = new HttpDate(second, DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochSecond(second).atZone(ZoneOffset.UTC)));
            date = current;
        }
        return current.text();
    }

    @Override
    public void routes(DirectRouteBuilder routes) {
        routes.GET("/plaintext").respond(direct -> direct.responses().ok(HELLO) // <3>
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .header(HttpHeaders.SERVER, "Micronaut")
            .header(HttpHeaders.DATE, date()));
        routes.GET("/json").respond(direct -> direct.responses().ok(new Message("Hello, World!")) // <4>
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.SERVER, "Micronaut")
            .header(HttpHeaders.DATE, date()));
    }
}
// end::clazz[]
