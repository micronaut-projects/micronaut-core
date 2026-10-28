package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.EventLoopGroup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * @OnMessage must not run before an asynchronous @OnOpen that rejects the session.
 */
class AsyncOnOpenRejectSpec extends Specification {

    void "messages are not dispatched before an async OnOpen rejects the session"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'AsyncOnOpenRejectSpec'])
        RejectingEndpoint.RECEIVED.clear()
        HttpClient httpClient = HttpClient.newHttpClient()
        def listener = new Listener()
        WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}/async-open/reject"), listener)
                .get(10, TimeUnit.SECONDS)

        when:
        ws.sendText('order', true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            listener.closeCode == 1008
        }
        RejectingEndpoint.RECEIVED.empty

        cleanup:
        ws?.abort()
        server?.close()
    }

    static class Listener implements WebSocket.Listener {
        volatile Integer closeCode

        @Override
        CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeCode = statusCode
            return null
        }
    }

    @Requires(property = 'spec.name', value = 'AsyncOnOpenRejectSpec')
    @ServerWebSocket("/async-open/reject")
    static class RejectingEndpoint {
        static final List<String> RECEIVED = new CopyOnWriteArrayList<>()
        private final EventLoopGroup eventLoopGroup

        RejectingEndpoint(EventLoopGroup eventLoopGroup) {
            this.eventLoopGroup = eventLoopGroup
        }

        @OnOpen
        CompletableFuture<Void> onOpen(WebSocketSession session) {
            // simulates an asynchronous credential check that fails
            CompletableFuture<Void> result = new CompletableFuture<>()
            // run the rejection on an event loop (the leak detector rejects allocations elsewhere)
            eventLoopGroup.next().schedule({
                session.close(CloseReason.POLICY_VIOLATION)
                result.complete(null)
            }, 1, TimeUnit.SECONDS)
            return result
        }

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            RECEIVED.add(message)
        }
    }
}
