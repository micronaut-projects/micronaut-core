package io.micronaut.http.server

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Flux
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A suspend handler bound to the frame content must see it after resuming, and its failure must
 * reach the endpoint's error handler: both need the handler's flow to complete with the coroutine
 * rather than immediately.
 */
@MicronautTest
@Property(name = "spec.name", value = "WebSocketSuspendByteBufTest")
class WebSocketSuspendByteBufTest {
    @Inject
    lateinit var server: EmbeddedServer

    @Inject
    lateinit var client: WebSocketClient

    @Inject
    lateinit var socket: SuspendByteBufSocket

    @Test
    @Timeout(20)
    fun suspendHandlerSeesTheFrameContentAfterResuming() {
        val cl = Flux.from(client.connect(TestClient::class.java, server.uri.toString() + "/suspend/bytebuf")).blockFirst()!!
        cl.send(byteArrayOf(1, 2, 3))
        waitFor { socket.received.size == 1 }
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(socket.received[0]))
        assertTrue(socket.errors.isEmpty(), socket.errors.toString())

        cl.send(byteArrayOf(9))
        waitFor { socket.errors.size == 1 }
        assertEquals("nine", socket.errors[0].message)
        cl.close()
    }

    private fun waitFor(condition: () -> Boolean) {
        while (!condition()) {
            Thread.sleep(50)
        }
    }

    @Requires(property = "spec.name", value = "WebSocketSuspendByteBufTest")
    @ServerWebSocket("/suspend/bytebuf")
    class SuspendByteBufSocket {
        val received = CopyOnWriteArrayList<ByteArray>()
        val errors = CopyOnWriteArrayList<Throwable>()

        @OnMessage
        suspend fun onMessage(message: ByteBuf, session: WebSocketSession) {
            // suspend before touching the content: it must still be alive afterwards
            delay(100)
            val bytes = ByteBufUtil.getBytes(message)
            if (bytes.size == 1 && bytes[0] == 9.toByte()) {
                throw IllegalStateException("nine")
            }
            received.add(bytes)
        }

        @OnError
        fun onError(error: Throwable) {
            errors.add(error)
        }
    }

    @Requires(property = "spec.name", value = "WebSocketSuspendByteBufTest")
    @ClientWebSocket("/suspend/bytebuf")
    abstract class TestClient : AutoCloseable {
        abstract fun send(msg: ByteArray)

        @OnMessage
        fun onMessage(msg: ByteArray) {
        }
    }
}
