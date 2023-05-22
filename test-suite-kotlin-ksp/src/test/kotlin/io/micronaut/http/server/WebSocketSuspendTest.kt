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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Flux
import spock.lang.Issue

@MicronautTest
@Property(name = "spec.name", value = "WebSocketSuspendTest")
class WebSocketSuspendTest {
    @Inject
    lateinit var server: EmbeddedServer

    @Inject
    lateinit var client: WebSocketClient

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/6582")
    @Test
    @Timeout(10)
    fun test() {
        val cl = Flux.from(client.connect(TestWebSocketClient::class.java, server.uri.toString() + "/demo/ws")).blockFirst()!!
        cl.send("foo")
        while (true) {
            Thread.sleep(100)
            if (cl.received == "foo") {
                break
            }
        }
        cl.close()
    }

    @Requires(property = "spec.name", value = "WebSocketSuspendTest")
    @ServerWebSocket("/demo/ws")
    class TestWebSocketController {
        @OnMessage
        suspend fun messageHandler(message: String, session: WebSocketSession) {
            delay(100)
            session.sendSync(message)
        }
    }

    @Requires(property = "spec.name", value = "WebSocketSuspendTest")
    @ClientWebSocket("/demo/ws")
    abstract class TestWebSocketClient : AutoCloseable {
        var received: String = ""

        abstract fun send(msg: String)

        @OnMessage
        fun onMessage(msg: String) {
            this.received = msg
        }
    }
}