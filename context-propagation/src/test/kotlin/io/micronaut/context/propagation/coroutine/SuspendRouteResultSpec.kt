package io.micronaut.context.propagation.coroutine

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import reactor.core.publisher.Flux

/**
 * The results a suspend route completes with after it has suspended, with the Reactor context
 * propagated to the coroutine (kotlinx-coroutines-reactor is on the classpath).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SuspendRouteResultSpec {

    private lateinit var server: EmbeddedServer
    private lateinit var client: HttpClient

    @BeforeAll
    fun start() {
        server = ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "SuspendRouteResultSpec"))
        client = server.applicationContext.createBean(HttpClient::class.java, server.url)
    }

    @AfterAll
    fun stop() {
        client.close()
        server.close()
    }

    private fun get(path: String): HttpResponse<String> =
        client.toBlocking().exchange(HttpRequest.GET<Any>("/suspend-result$path"), String::class.java)

    @Test
    fun `a value produced after suspending is the body`() {
        assertEquals("value", get("/value").body())
    }

    @Test
    fun `a value produced on an executor after suspending is the body`() {
        assertEquals("io", get("/io").body())
    }

    @Test
    fun `a null result after suspending is a not found`() {
        val ex = assertThrows(HttpClientResponseException::class.java) { get("/null") }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun `an HTTP response with a streamed body after suspending is streamed`() {
        val response = get("/flow")
        assertEquals(HttpStatus.ACCEPTED, response.status)
        assertEquals("[a,b]", response.body())
    }

    @Requires(property = "spec.name", value = "SuspendRouteResultSpec")
    @Controller("/suspend-result")
    class SuspendResultController {

        @Get("/value", produces = [MediaType.TEXT_PLAIN])
        suspend fun value(): String {
            delay(1)
            return "value"
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get("/io", produces = [MediaType.TEXT_PLAIN])
        suspend fun io(): String {
            delay(1)
            return "io"
        }

        @Get("/null")
        suspend fun nothing(): String? {
            delay(1)
            return null
        }

        @Get("/flow", produces = [MediaType.APPLICATION_JSON])
        suspend fun flux(): HttpResponse<Flux<String>> {
            delay(1)
            return HttpResponse.accepted<Flux<String>>().body(Flux.just("a", "b"))
        }
    }
}
