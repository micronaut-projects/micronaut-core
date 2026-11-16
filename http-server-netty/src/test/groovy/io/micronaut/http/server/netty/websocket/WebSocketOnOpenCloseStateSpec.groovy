package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.netty.websocket.AbstractNettyWebSocketHandler
import io.micronaut.http.netty.websocket.NettyWebSocketSession
import io.micronaut.http.netty.websocket.WebSocketSessionRepository
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.micronaut.websocket.exceptions.WebSocketSessionException
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebSocketOnOpenCloseStateSpec extends Specification {
    void 'OnOpen sees the registered open session before handler installation and can close it once'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketOnOpenCloseStateSpec'])
        ClosingSocket bean = server.applicationContext.getBean(ClosingSocket)

        when:
        new Socket('127.0.0.1', server.port).withCloseable { socket ->
            socket.soTimeout = 5000
            socket.outputStream.write(('GET /on-open-close-state HTTP/1.1\r\n' +
                    'Host: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n' +
                    'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n')
                    .getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            while (socket.inputStream.read() != -1) {
                // Consume the handshake and close frame, allowing the close to finish.
            }
        }
        def observed = bean.observed.get(5, TimeUnit.SECONDS)

        then:
        observed == [open: true, registered: true, handlerInstalled: false, closedAfterClose: true, sendRejected: true]
        bean.messages.get() == 0

        cleanup:
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'WebSocketOnOpenCloseStateSpec')
    @ServerWebSocket('/on-open-close-state')
    static class ClosingSocket {
        @Inject
        WebSocketSessionRepository repository
        final CompletableFuture<Map> observed = new CompletableFuture<>()
        final AtomicInteger messages = new AtomicInteger()

        @OnOpen
        void open(WebSocketSession session) {
            try {
                def channel = repository.channelGroup.find { it.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY).get().is(session) }
                def state = [open: session.open, registered: channel != null,
                             handlerInstalled: channel.pipeline().get(AbstractNettyWebSocketHandler) != null]
                session.close()
                session.close()
                state.closedAfterClose = !session.open
                state.sendRejected = false
                try {
                    session.sendAsync('after close')
                } catch (WebSocketSessionException ignored) {
                    state.sendRejected = true
                }
                observed.complete(state)
            } catch (Throwable t) {
                observed.completeExceptionally(t)
                throw t
            }
        }

        @OnMessage
        void message(String message) {
            messages.incrementAndGet()
        }
    }
}
