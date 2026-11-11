package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
// end::imports[]

@Requires(property = "spec.name", value = "StreamRoutesTest")
// tag::clazz[]
@Singleton
public class StreamRoutes implements HttpRoutes {

    private final TaskScheduler scheduler;

    public StreamRoutes(@Named(TaskExecutors.SCHEDULED) TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.GET("/countdown/{from}").executeOn(TaskExecutors.BLOCKING).sse((request, pathVariables, events) -> { // <1>
            for (int i = pathVariables.getInt("from"); i > 0; i--) {
                events.send(Event.of(i).id(String.valueOf(i))).toCompletableFuture().join(); // <2>
            }
        });
        routes.GET("/ticks").sse((request, pathVariables, events) -> {
            events.keepOpen().heartbeat(Duration.ofSeconds(15)); // <3>
            AtomicInteger count = new AtomicInteger();
            ScheduledFuture<?> ticks = scheduler.scheduleAtFixedRate(Duration.ZERO, Duration.ofMillis(10), () -> {
                int tick = count.incrementAndGet();
                if (tick > 3) {
                    events.complete(); // <4>
                } else if (events.isWritable()) { // <5>
                    events.send("tick " + tick);
                }
            });
            events.onClose(error -> ticks.cancel(false)); // <6>
        });
        routes.POST("/words").consumes(MediaType.TEXT_PLAIN_TYPE).body(String.class).sse((request, pathVariables, text, events) -> { // <7>
            for (String word : text.split(" ")) {
                events.send(word);
            }
        });
        routes.POST("/messages").consumes(MediaType.TEXT_PLAIN_TYPE)
            .produces(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE) // <11>
            .body(String.class).sse((request, pathVariables, message, events) -> {
            events.header("Session-Id", "s-1"); // <9>
            switch (message) {
                case "notify" -> events.respond(HttpResponse.accepted()); // <10>
                case "ping" -> events.respond(HttpResponse.ok(Map.of("result", "pong")));
                default -> events.send("received " + message);
            }
        });
        routes.GET("/orders/{id}/updates").sse((request, pathVariables, events) -> {
            int id = pathVariables.getInt("id");
            if (id != 1) {
                throw new HttpStatusException(HttpStatus.NOT_FOUND, "No order " + id); // <12>
            }
            events.send("order " + id + " shipped");
        });
        routes.GET("/jobs/{id}").sse((request, pathVariables, events) -> {
            events.send("started");
            if (pathVariables.getInt("id") != 1) {
                events.send(Event.of("the job failed").name("error")); // <13>
                return;
            }
            events.send("done");
        });
        routes.GET("/feed").sse((request, pathVariables, events) -> {
            int last = events.lastEventId().map(Integer::parseInt).orElse(0); // <14>
            for (int i = last + 1; i <= 3; i++) {
                Event<String> event = Event.of("item " + i).id(String.valueOf(i));
                if (i == last + 1) {
                    event.retry(Duration.ofSeconds(5)); // <15>
                }
                events.send(event);
            }
        });
        routes.GET("/numbers", (request, pathVariables) -> {
            Iterator<Integer> numbers = List.of(1, 2, 3).iterator();
            return HttpResponse.ok(BodyElements.of(() -> // <8>
                CompletableFuture.completedFuture(numbers.hasNext() ? Optional.of(numbers.next()) : Optional.<Integer>empty())));
        });
    }
}
// end::clazz[]
