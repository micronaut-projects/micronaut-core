package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.web.router.builder.DirectContext
import io.micronaut.web.router.builder.DirectRouteBuilder
import io.micronaut.web.router.builder.HttpDirectRoutes
import jakarta.inject.Singleton

import java.nio.charset.StandardCharsets
import java.util.function.Function
// end::imports[]

@Requires(property = "spec.name", value = "BenchmarkRoutesSpec")
// tag::clazz[]
@Singleton
class BenchmarkRoutes implements HttpDirectRoutes {

    private static final byte[] HELLO = "Hello, World!".getBytes(StandardCharsets.US_ASCII) // <1>

    @Override
    void routes(DirectRouteBuilder routes) {
        routes.GET("/plaintext").respond({ DirectContext direct -> // <2>
            direct.responses().ok(HELLO)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header(HttpHeaders.SERVER, "Micronaut") // <3>
        } as Function<DirectContext, HttpResponse<?>>)
        routes.GET("/json").respond({ DirectContext direct -> // <4>
            direct.responses().ok(new Message("Hello, World!"))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.SERVER, "Micronaut")
        } as Function<DirectContext, HttpResponse<?>>)
    }
}

@Introspected
record Message(String message) {
}
// end::clazz[]
