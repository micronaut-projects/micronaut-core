package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
// end::imports[]

@Requires(property = "spec.name", value = "RespondRoutesTest")
// tag::clazz[]
@Singleton
public class RespondRoutes implements HttpRoutes {

    private final AtomicInteger visits = new AtomicInteger();

    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.GET("/ping") // <1>
            .after((request, response) -> response.header("Cache-Control", "max-age=60")).and() // <2>
            .respond(HttpResponse.ok("pong").contentType(MediaType.TEXT_PLAIN_TYPE));
        routes.GET("/old-ping").respond(HttpResponse.permanentRedirect(URI.create("/ping"))); // <3>
        routes.GET("/visits").respond(() ->
            HttpResponse.ok("visit " + visits.incrementAndGet()).contentType(MediaType.TEXT_PLAIN_TYPE)); // <4>
        routes.GET("/greetings/{name}").respond(pathVariables ->
            HttpResponse.ok("Hello " + pathVariables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE)); // <5>
        routes.POST("/legacy/webhook").respond(HttpResponse.status(HttpStatus.GONE)); // <6>
    }
}
// end::clazz[]
