package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ResponseElements;
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
                events.sendAndAwait(Event.of(i).id(String.valueOf(i))); // <2>
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
        routes.GET("/numbers", (request, pathVariables) -> {
            Iterator<Integer> numbers = List.of(1, 2, 3).iterator();
            return HttpResponse.ok(ResponseElements.of(() -> // <8>
                CompletableFuture.completedFuture(numbers.hasNext() ? Optional.of(numbers.next()) : Optional.<Integer>empty())));
        });
    }
}
// end::clazz[]
