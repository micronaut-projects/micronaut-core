package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.EventLoopGroup
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
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
        WebSocketClient wsClient = server.applicationContext.createBean(WebSocketClient, server.URI)
        TextClientWebSocket ws = Mono.from(wsClient.connect(TextClientWebSocket, '/async-open/reject')).block(Duration.ofSeconds(10))

        when:
        ws.sendText('order').get(10, TimeUnit.SECONDS)

        then:
        ws.closeReason.get(10, TimeUnit.SECONDS).code == CloseReason.POLICY_VIOLATION.code
        RejectingEndpoint.RECEIVED.empty

        cleanup:
        ws?.close()
        wsClient?.close()
        server?.close()
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
