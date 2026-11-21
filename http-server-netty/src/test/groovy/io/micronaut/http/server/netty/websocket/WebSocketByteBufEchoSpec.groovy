package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.buffer.ByteBuf
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class WebSocketByteBufEchoSpec extends Specification {
    void 'a session keeps reading after the handler echoes the retained ByteBuf payload it received'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketByteBufEchoSpec'])
        def received = new LinkedBlockingQueue<String>()
        def client = HttpClient.newHttpClient()
        def socket = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:${server.port}/byte-buf-echo"), new WebSocket.Listener() {
                    @Override
                    CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                        byte[] bytes = new byte[data.remaining()]
                        data.get(bytes)
                        received.add(new String(bytes))
                        webSocket.request(1)
                        return null
                    }
                }).get(5, TimeUnit.SECONDS)

        when:
        // the write of the echo consumes a reference, so the handler retains the payload before
        // sending it; the framework releases its own reference once the handler completes and
        // resumes reading
        socket.sendBinary(ByteBuffer.wrap('first'.bytes), true).get(5, TimeUnit.SECONDS)
        def first = received.poll(5, TimeUnit.SECONDS)
        socket.sendBinary(ByteBuffer.wrap('second'.bytes), true).get(5, TimeUnit.SECONDS)
        def second = received.poll(5, TimeUnit.SECONDS)

        then:
        first == 'first'
        second == 'second'

        cleanup:
        socket?.abort()
        client?.close()
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'WebSocketByteBufEchoSpec')
    @ServerWebSocket('/byte-buf-echo')
    static class EchoSocket {
        @OnMessage
        void message(ByteBuf message, WebSocketSession session) {
            session.sendSync(message.retain())
        }
    }
}
