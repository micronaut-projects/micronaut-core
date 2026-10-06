package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.web.router.direct.DirectContext
import io.micronaut.web.router.direct.DirectRouteBuilder
import io.micronaut.web.router.direct.HttpDirectRoutes
import jakarta.inject.Singleton

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.function.Function
// end::imports[]

@Requires(property = "spec.name", value = "BenchmarkRoutesSpec")
// tag::clazz[]
@Singleton
class BenchmarkRoutes implements HttpDirectRoutes {

    private static final byte[] HELLO = "Hello, World!".getBytes(StandardCharsets.US_ASCII) // <1>

    private volatile HttpDate date = new HttpDate(-1, "") // <2>

    /**
     * @return The Date header value, formatted at most once per second
     */
    String date() {
        long second = System.currentTimeMillis().intdiv(1000)
        HttpDate current = date
        if (current.second() != second) {
            current = new HttpDate(second, DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochSecond(second).atZone(ZoneOffset.UTC)))
            date = current
        }
        return current.text()
    }

    @Override
    void routes(DirectRouteBuilder routes) {
        routes.GET("/plaintext").respond({ DirectContext direct -> // <3>
            direct.responses().ok(HELLO)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header(HttpHeaders.SERVER, "Micronaut")
                .header(HttpHeaders.DATE, date())
        } as Function<DirectContext, HttpResponse<?>>)
        routes.GET("/json").respond({ DirectContext direct -> // <4>
            direct.responses().ok(new Message("Hello, World!"))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.SERVER, "Micronaut")
                .header(HttpHeaders.DATE, date())
        } as Function<DirectContext, HttpResponse<?>>)
    }
}

@Introspected
record Message(String message) {
}

record HttpDate(long second, String text) {
}
// end::clazz[]
