package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class WebSocketRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "WebSocketRoutesSpec"])
    @Shared @AutoCleanup HttpClient http = HttpClient.newHttpClient()

    void "the handlers answer the connection"() {
        given:
        Messages messages = new Messages()
        WebSocket ws = connect("/echo/World", messages)

        expect:
        messages.next() == "Hello World"

        when:
        ws.sendText("hi", true).get(5, TimeUnit.SECONDS)

        then:
        messages.next() == "echo hi"

        cleanup:
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS)
    }

    void "the filters of the route apply to the upgrade"() {
        when:
        connect("/private", new Messages())

        then:
        ExecutionException rejected = thrown()
        (rejected.cause as WebSocketHandshakeException).response.statusCode() == 403

        when:
        Messages messages = new Messages()
        WebSocket ws = connect("/private", messages, "secret")

        then:
        messages.next() == "welcome"

        cleanup:
        ws?.sendClose(WebSocket.NORMAL_CLOSURE, "done")?.get(5, TimeUnit.SECONDS)
    }

    void "the messages are streams"() {
        given:
        Messages ticks = new Messages()
        WebSocket ticking = connect("/ticks", ticks)

        expect:
        ticks.next() == "tick 1"
        ticks.next() == "tick 2"
        ticks.next() == "tick 3"

        when:
        Messages upper = new Messages()
        WebSocket ws = connect("/upper", upper)
        ws.sendText("hello", true).get(5, TimeUnit.SECONDS)
        ws.sendText("world", true).get(5, TimeUnit.SECONDS)

        then:
        upper.next() == "HELLO"
        upper.next() == "WORLD"

        cleanup:
        ticking.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS)
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS)
    }

    void "the jobs are handled concurrently"() {
        given:
        Messages messages = new Messages()
        WebSocket ws = connect("/jobs", messages)

        when:
        ws.sendText("1", true).get(5, TimeUnit.SECONDS)
        ws.sendText("2", true).get(5, TimeUnit.SECONDS)

        then: 'up to 4 at the same time: in any order'
        [messages.next(), messages.next()] as Set == ["done 1", "done 2"] as Set

        cleanup:
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS)
    }

    private WebSocket connect(String path, Messages messages, String token = null) {
        WebSocket.Builder builder = http.newWebSocketBuilder()
        if (token != null) {
            builder.header("X-Token", token)
        }
        builder.buildAsync(URI.create("ws://localhost:${server.port}$path"), messages).get(5, TimeUnit.SECONDS)
    }

    /**
     * The text messages of a connection.
     */
    static class Messages implements WebSocket.Listener {
        private final BlockingQueue<String> received = new LinkedBlockingQueue<>()
        private final StringBuilder text = new StringBuilder()

        @Override
        CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data)
            if (last) {
                received.add(text.toString())
                text.setLength(0)
            }
            webSocket.request(1)
            null
        }

        String next() {
            String message = received.poll(5, TimeUnit.SECONDS)
            if (message == null) {
                throw new AssertionError("no message")
            }
            message
        }
    }
}
