package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.util.Locale;
// end::imports[]

@Requires(property = "spec.name", value = "WebSocketRoutesTest")
@Singleton
public class WebSocketRoutes implements HttpRoutes {

    @Override
    public void routes(HttpRouteBuilder routes) {
        // tag::echo[]
        routes.GET("/echo/{name}").webSocket(ws -> ws // <1>
            .onOpen((session, request) -> // <2>
                session.sendAsync("Hello " + session.getUriVariables().get("name", String.class).orElseThrow()))
            .onMessage(String.class, (session, message) -> session.sendAsync("echo " + message)) // <3>
            .onError((session, error) -> session.sendAsync("error " + error.getMessage())) // <4>
            .onClose((session, reason) -> null)); // <5>
        // end::echo[]

        // tag::filter[]
        routes.GET("/private")
            .beforeReplacing(request -> request.getHeaders().contains("X-Token") // <1>
                ? null
                : HttpResponse.status(HttpStatus.FORBIDDEN))
            .and()
            .webSocket(ws -> ws
                .onOpen((session, request) -> session.sendAsync("welcome")));
        // end::filter[]

        // tag::streams[]
        routes.GET("/ticks").webSocket(ws -> ws
            .onOpen((session, request) -> session.sendAllAsync(Flux.range(1, 3).map(i -> "tick " + i)))); // <1>
        routes.GET("/upper").webSocket(ws -> ws
            .onMessageStream(String.class, (session, messages) -> // <2>
                session.sendAllAsync(Flux.from(messages).map(message -> message.toUpperCase(Locale.ROOT))))); // <3>
        // end::streams[]

        // tag::delivery[]
        routes.GET("/jobs")
            .executeOn(TaskExecutors.BLOCKING) // <1>
            .webSocket(ws -> ws
                .maxConcurrentMessages(4) // <2>
                .maxPendingMessages(32) // <3>
                .onMessage(String.class, (session, job) -> session.sendAsync("done " + job)));
        // end::delivery[]
    }
}
