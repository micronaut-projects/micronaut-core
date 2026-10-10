package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebSocketRoutesTest {

    private val server = ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>("spec.name" to "WebSocketRoutesTest"))
    private val http = HttpClient.newHttpClient()

    @AfterAll
    fun stop() {
        http.close()
        server.close()
    }

    @Test
    fun theHandlersAnswerTheConnection() {
        val messages = Messages()
        val ws = connect("/echo/World", messages)
        assertEquals("Hello World", messages.next())
        ws.sendText("hi", true).get(5, TimeUnit.SECONDS)
        assertEquals("echo hi", messages.next())
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS)
    }

    @Test
    fun theFiltersOfTheRouteApplyToTheUpgrade() {
        val rejected = assertThrows(ExecutionException::class.java) { connect("/private", Messages()) }
        assertEquals(403, assertInstanceOf(WebSocketHandshakeException::class.java, rejected.cause).response.statusCode())

        val messages = Messages()
        val ws = connect("/private", messages, "secret")
        assertEquals("welcome", messages.next())
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS)
    }

    @Test
    fun theMessagesAreStreams() {
        val ticks = Messages()
        val ticking = connect("/ticks", ticks)
        assertEquals("tick 1", ticks.next())
        assertEquals("tick 2", ticks.next())
        assertEquals("tick 3", ticks.next())
        ticking.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS)

        val upper = Messages()
        val ws = connect("/upper", upper)
        ws.sendText("hello", true).get(5, TimeUnit.SECONDS)
        ws.sendText("world", true).get(5, TimeUnit.SECONDS)
        assertEquals("HELLO", upper.next())
        assertEquals("WORLD", upper.next())
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS)
    }

    @Test
    fun theJobsAreHandledConcurrently() {
        val messages = Messages()
        val ws = connect("/jobs", messages)
        ws.sendText("1", true).get(5, TimeUnit.SECONDS)
        ws.sendText("2", true).get(5, TimeUnit.SECONDS)
        // up to 4 at the same time: in any order
        assertEquals(setOf("done 1", "done 2"), setOf(messages.next(), messages.next()))
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS)
    }

    private fun connect(path: String, messages: Messages, token: String? = null): WebSocket {
        val builder = http.newWebSocketBuilder()
        if (token != null) {
            builder.header("X-Token", token)
        }
        return builder.buildAsync(URI.create("ws://localhost:${server.port}$path"), messages).get(5, TimeUnit.SECONDS)
    }

    /**
     * The text messages of a connection.
     */
    private class Messages : WebSocket.Listener {
        private val received = LinkedBlockingQueue<String>()
        private val text = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) {
                received.add(text.toString())
                text.setLength(0)
            }
            webSocket.request(1)
            return null
        }

        fun next(): String = received.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("no message")
    }
}
