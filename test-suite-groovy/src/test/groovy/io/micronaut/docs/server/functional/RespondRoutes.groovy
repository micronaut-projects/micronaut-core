package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.PathVariables
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.function.Supplier
// end::imports[]

@Requires(property = "spec.name", value = "RespondRoutesSpec")
// tag::clazz[]
@Singleton
class RespondRoutes implements HttpRoutes {

    private final AtomicInteger visits = new AtomicInteger()

    @Override
    void routes(HttpRouteBuilder routes) {
        routes.GET("/ping") // <1>
            .after { request, response -> response.header("Cache-Control", "max-age=60") }.and() // <2>
            .respond(HttpResponse.ok("pong").contentType(MediaType.TEXT_PLAIN_TYPE))
        routes.GET("/old-ping").respond(HttpResponse.permanentRedirect(URI.create("/ping"))) // <3>
        routes.GET("/visits").respond({
            HttpResponse.ok("visit " + visits.incrementAndGet()).contentType(MediaType.TEXT_PLAIN_TYPE)
        } as Supplier<HttpResponse<?>>) // <4>
        routes.GET("/greetings/{name}").respond({ PathVariables pathVariables ->
            HttpResponse.ok("Hello " + pathVariables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE)
        } as Function<PathVariables, HttpResponse<?>>) // <5>
        routes.POST("/legacy/webhook").respond(HttpResponse.status(HttpStatus.GONE)) // <6>
    }
}
// end::clazz[]
