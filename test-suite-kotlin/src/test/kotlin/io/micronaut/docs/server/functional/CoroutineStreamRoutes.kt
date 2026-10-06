package io.micronaut.docs.server.functional

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.body.asResponseElements
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.sse.launch
import io.micronaut.http.sse.sendAwait
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.CompletableFuture

@Requires(property = "spec.name", value = "CoroutineStreamRoutesTest")
@Singleton
class CoroutineStreamRoutes : HttpRoutes {

    /**
     * Completes when the stream of /coroutine/endless was cancelled.
     */
    val cancelled = CompletableFuture<Throwable?>()

    override fun routes(routes: HttpRouteBuilder) {
        routes.GET("/coroutine/ticks").sse { _, _, events ->
            events.launch {
                for (tick in 1..3) {
                    sendAwait("tick $tick")
                    delay(5)
                }
            }
        }
        routes.GET("/coroutine/refuse").sse { _, _, events ->
            events.launch {
                delay(5)
                throw HttpStatusException(HttpStatus.NOT_FOUND, "no such stream")
            }
        }
        routes.GET("/coroutine/endless").sse { _, _, events ->
            events.onClose { cancelled.complete(it) }
            events.launch {
                var tick = 0
                while (true) {
                    sendAwait("tick ${tick++}")
                    delay(5)
                }
            }
        }
        routes.GET("/coroutine/numbers") { _, _ ->
            HttpResponse.ok(flowOf(1, 2, 3).asResponseElements())
        }
        routes.GET("/coroutine/refused-numbers") { _, _ ->
            HttpResponse.ok(flow<Int> { throw HttpStatusException(HttpStatus.CONFLICT, "conflict") }.asResponseElements())
        }
    }
}
