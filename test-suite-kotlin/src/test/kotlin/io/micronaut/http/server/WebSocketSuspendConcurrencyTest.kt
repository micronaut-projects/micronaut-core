package io.micronaut.http.server

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Flux
import spock.lang.Issue
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@MicronautTest
@Property(name = "spec.name", value = "WebSocketSuspendConcurrencyTest")
class WebSocketSuspendConcurrencyTest {
    @Inject
    lateinit var server: EmbeddedServer

    @Inject
    lateinit var client: WebSocketClient

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/8663")
    @Test
    @Timeout(20)
    fun suspendOnMessageHandlesRapidMessages() {
        val ws = Flux.from(client.connect(TestWebSocketClient::class.java, server.uri.toString() + "/demo/ws-concurrent")).blockFirst(Duration.ofSeconds(10))!!
        repeat(TOTAL_MESSAGES) { i ->
            ws.send("m-$i")
        }
        assertTrue(ws.receivedLatch.await(20, TimeUnit.SECONDS), "Did not receive all websocket echoes")
        ws.close()
    }

    @Requires(property = "spec.name", value = "WebSocketSuspendConcurrencyTest")
    @ServerWebSocket("/demo/ws-concurrent")
    class TestWebSocketController {
        @OnMessage
        suspend fun messageHandler(message: String, session: WebSocketSession) {
            delay(5)
            session.sendSync(message)
        }
    }

    @Requires(property = "spec.name", value = "WebSocketSuspendConcurrencyTest")
    @ClientWebSocket("/demo/ws-concurrent")
    abstract class TestWebSocketClient : AutoCloseable {
        val receivedLatch = CountDownLatch(TOTAL_MESSAGES)

        abstract fun send(msg: String)

        @OnMessage
        fun onMessage(msg: String) {
            receivedLatch.countDown()
        }
    }

    companion object {
        private const val TOTAL_MESSAGES = 200
    }
}
