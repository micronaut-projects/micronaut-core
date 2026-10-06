package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.exceptions.ConnectionClosedException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * The coroutine extensions of the streams: SseEmitter.launch and sendAwait, and Flow.asResponseElements.
 */
class CoroutineStreamRoutesTest {

    @Test
    fun coroutinesStreamTheResponses() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "CoroutineStreamRoutesTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val http = client.toBlocking()
                // the stream ends when the coroutine returns
                assertEquals("data: tick 1\n\ndata: tick 2\n\ndata: tick 3\n\n", http.retrieve(HttpRequest.GET<Any>("/coroutine/ticks")))
                // an exception of the coroutine before the first event is answered by the error routes
                val refused = assertThrows(HttpClientResponseException::class.java) {
                    http.retrieve(HttpRequest.GET<Any>("/coroutine/refuse"))
                }
                assertEquals(HttpStatus.NOT_FOUND, refused.status)
                assertEquals("[1,2,3]", http.retrieve(HttpRequest.GET<Any>("/coroutine/numbers")))
                val conflict = assertThrows(HttpClientResponseException::class.java) {
                    http.retrieve(HttpRequest.GET<Any>("/coroutine/refused-numbers"))
                }
                assertEquals(HttpStatus.CONFLICT, conflict.status)
            }
            // the coroutine is cancelled when the client disconnects
            Socket("localhost", server.port).use { socket ->
                socket.getOutputStream().write("GET /coroutine/endless HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                val buffer = ByteArray(8192)
                var received = ""
                while (!received.contains("data: tick 1")) {
                    val n = socket.getInputStream().read(buffer)
                    received += String(buffer, 0, n, StandardCharsets.ISO_8859_1)
                }
            }
            val routes = server.applicationContext.getBean(CoroutineStreamRoutes::class.java)
            assertInstanceOf(ConnectionClosedException::class.java, routes.cancelled.get(20, TimeUnit.SECONDS))
        }
    }
}
