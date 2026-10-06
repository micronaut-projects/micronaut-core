from micronaut.context.annotation import Requires
# tag::imports[]
import itertools
from typing import Annotated

import java
from jakarta.inject import Named, Singleton
from micronaut.http import HttpResponse, MediaType
from micronaut.http.body import ResponseElements
from micronaut.http.sse import Event
from micronaut.scheduling import TaskExecutors, TaskScheduler
from micronaut.web.router.builder import HttpRouteBuilder, HttpRoutes

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
Duration = java.type("java.time.Duration")
Map = java.type("java.util.Map")
Optional = java.type("java.util.Optional")
String = java.type("java.lang.String")
# end::imports[]


@Requires(property="spec.name", value="StreamRoutesTest")
# tag::clazz[]
@Singleton
class StreamRoutes(HttpRoutes):

    def __init__(self, scheduler: Annotated[TaskScheduler, Named(TaskExecutors.SCHEDULED)]):
        self.scheduler = scheduler

    def routes(self, routes: HttpRouteBuilder) -> None:
        def countdown(request, path_variables, events):
            for i in range(path_variables.getInt("from"), 0, -1):
                events.sendAndAwait(Event.of(i).id(str(i)))  # <2>

        routes.GET("/countdown/{from}").executeOn(TaskExecutors.BLOCKING).sse(countdown)  # <1>

        def ticks(request, path_variables, events):
            events.keepOpen().heartbeat(Duration.ofSeconds(15))  # <3>
            count = itertools.count(1)

            def tick():
                n = next(count)
                if n > 3:
                    events.complete()  # <4>
                elif events.isWritable():  # <5>
                    events.send(f"tick {n}")

            task = self.scheduler.scheduleAtFixedRate(Duration.ZERO, Duration.ofMillis(10), tick)
            events.onClose(lambda error: task.cancel(False))  # <6>

        routes.GET("/ticks").sse(ticks)

        def words(request, path_variables, text, events):
            for word in text.split(" "):
                events.send(word)

        routes.POST("/words").consumes(MediaType.TEXT_PLAIN_TYPE).body(String).sse(words)  # <7>

        def messages(request, path_variables, message, events):
            events.header("Session-Id", "s-1")  # <9>
            if message == "notify":
                events.respond(HttpResponse.accepted())  # <10>
            elif message == "ping":
                events.respond(HttpResponse.ok(Map.of("result", "pong")))
            else:
                events.send(f"received {message}")

        (routes.POST("/messages").consumes(MediaType.TEXT_PLAIN_TYPE)
            .produces(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)  # <11>
            .body(String).sse(messages))

        def numbers(request, path_variables):
            remaining = iter([1, 2, 3])
            return HttpResponse.ok(ResponseElements.of(lambda:  # <8>
                                   CompletableFuture.completedFuture(Optional.ofNullable(next(remaining, None)))))

        routes.GET("/numbers", numbers)
# end::clazz[]
