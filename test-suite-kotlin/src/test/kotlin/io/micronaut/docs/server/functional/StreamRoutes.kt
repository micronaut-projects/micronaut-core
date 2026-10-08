package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.body.BodyElements
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.sse.Event
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.TaskScheduler
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
// end::imports[]

@Requires(property = "spec.name", value = "StreamRoutesTest")
// tag::clazz[]
@Singleton
class StreamRoutes(@Named(TaskExecutors.SCHEDULED) private val scheduler: TaskScheduler) : HttpRoutes {

    override fun routes(routes: HttpRouteBuilder) {
        routes.GET("/countdown/{from}").executeOn(TaskExecutors.BLOCKING).sse { request, pathVariables, events -> // <1>
            for (i in pathVariables.getInt("from") downTo 1) {
                events.sendAndAwait(Event.of(i).id(i.toString())) // <2>
            }
        }
        routes.GET("/ticks").sse { request, pathVariables, events ->
            events.keepOpen().heartbeat(Duration.ofSeconds(15)) // <3>
            val count = AtomicInteger()
            val ticks = scheduler.scheduleAtFixedRate(Duration.ZERO, Duration.ofMillis(10)) {
                val tick = count.incrementAndGet()
                if (tick > 3) {
                    events.complete() // <4>
                } else if (events.isWritable) { // <5>
                    events.send("tick $tick")
                }
            }
            events.onClose { ticks.cancel(false) } // <6>
        }
        routes.POST("/words").consumes(MediaType.TEXT_PLAIN_TYPE).body(String::class.java).sse { request, pathVariables, text, events -> // <7>
            for (word in text!!.split(" ")) {
                events.send(word)
            }
        }
        routes.POST("/messages").consumes(MediaType.TEXT_PLAIN_TYPE)
            .produces(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE) // <11>
            .body(String::class.java).sse { request, pathVariables, message, events ->
            events.header("Session-Id", "s-1") // <9>
            when (message) {
                "notify" -> events.respond(HttpResponse.accepted<Any>()) // <10>
                "ping" -> events.respond(HttpResponse.ok(mapOf("result" to "pong")))
                else -> events.send("received $message")
            }
        }
        routes.GET("/orders/{id}/updates").sse { request, pathVariables, events ->
            val id = pathVariables.getInt("id")
            if (id != 1) {
                throw HttpStatusException(HttpStatus.NOT_FOUND, "No order $id") // <12>
            }
            events.send("order $id shipped")
        }
        routes.GET("/jobs/{id}").sse { request, pathVariables, events ->
            events.send("started")
            if (pathVariables.getInt("id") != 1) {
                events.send(Event.of("the job failed").name("error")) // <13>
                return@sse
            }
            events.send("done")
        }
        routes.GET("/feed").sse { request, pathVariables, events ->
            val last = events.lastEventId().map { it.toInt() }.orElse(0) // <14>
            for (i in last + 1..3) {
                val event = Event.of("item $i").id(i.toString())
                if (i == last + 1) {
                    event.retry(Duration.ofSeconds(5)) // <15>
                }
                events.send(event)
            }
        }
        routes.GET("/numbers") { request, pathVariables ->
            val numbers = listOf(1, 2, 3).iterator()
            HttpResponse.ok(BodyElements.of { // <8>
                CompletableFuture.completedFuture(if (numbers.hasNext()) Optional.of(numbers.next()) else Optional.empty())
            })
        }
    }
}
// end::clazz[]
