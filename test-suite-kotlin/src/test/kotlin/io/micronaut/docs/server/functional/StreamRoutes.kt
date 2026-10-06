package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.body.ResponseElements
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
        routes.POST("/messages").consumes(MediaType.TEXT_PLAIN_TYPE).body(String::class.java).sse { request, pathVariables, message, events ->
            events.header("Session-Id", "s-1") // <9>
            when (message) {
                "notify" -> events.respond(HttpResponse.accepted<Any>()) // <10>
                "ping" -> events.respond(HttpResponse.ok(mapOf("result" to "pong")))
                else -> events.send("received $message")
            }
        }
        routes.GET("/numbers") { request, pathVariables ->
            val numbers = listOf(1, 2, 3).iterator()
            HttpResponse.ok(ResponseElements.of { // <8>
                CompletableFuture.completedFuture(if (numbers.hasNext()) Optional.of(numbers.next()) else Optional.empty())
            })
        }
    }
}
// end::clazz[]
