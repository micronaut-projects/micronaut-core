package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Consumes
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class FragmentedMessageWebSocketSpec extends Specification {

    void "a POJO message split into continuation frames is decoded from the complete payload"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FragmentedMessageWebSocketSpec'])
        HttpClient httpClient = HttpClient.newHttpClient()
        def listener = new CollectingListener()
        WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${embeddedServer.port}/fragmented/pojo"), listener)
                .get(10, TimeUnit.SECONDS)

        when:
        ws.sendText('{"text":', false).get(10, TimeUnit.SECONDS)
        ws.sendText('"hello ', false).get(10, TimeUnit.SECONDS)
        ws.sendText('fragments"}', true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            listener.messages == ['hello fragments']
        }

        cleanup:
        ws?.abort()
        httpClient?.close()
        embeddedServer?.close()
    }

    void "a malformed fragmented POJO message closes the session with an internal error"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FragmentedMessageWebSocketSpec'])
        HttpClient httpClient = HttpClient.newHttpClient()
        def listener = new CollectingListener()
        WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${embeddedServer.port}/fragmented/pojo"), listener)
                .get(10, TimeUnit.SECONDS)

        when:
        ws.sendText('{"text":', false).get(10, TimeUnit.SECONDS)
        ws.sendText('"not closed', true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            listener.closeCode == 1011
        }
        listener.messages.empty

        cleanup:
        ws?.abort()
        httpClient?.close()
        embeddedServer?.close()
    }

    void "a fragmented message for a media type without a decoder closes the session as unsupported data"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FragmentedMessageWebSocketSpec'])
        HttpClient httpClient = HttpClient.newHttpClient()
        def listener = new CollectingListener()
        WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${embeddedServer.port}/fragmented/undecodable"), listener)
                .get(10, TimeUnit.SECONDS)

        when:
        ws.sendText('{"text":', false).get(10, TimeUnit.SECONDS)
        ws.sendText('"hello"}', true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            listener.closeCode == 1003
        }
        listener.messages.empty

        cleanup:
        ws?.abort()
        httpClient?.close()
        embeddedServer?.close()
    }

    static class CollectingListener implements WebSocket.Listener {
        final List<String> messages = new CopyOnWriteArrayList<>()
        volatile Integer closeCode
        private StringBuilder current = new StringBuilder()

        @Override
        CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeCode = statusCode
            return null
        }

        @Override
        CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            current.append(data)
            if (last) {
                messages.add(current.toString())
                current = new StringBuilder()
            }
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }
    }

    @Requires(property = 'spec.name', value = 'FragmentedMessageWebSocketSpec')
    @ServerWebSocket("/fragmented/pojo")
    static class PojoServerWebSocket {
        @OnMessage
        Publisher<String> onMessage(Message message, WebSocketSession session) {
            return session.send(message.text)
        }
    }

    @Requires(property = 'spec.name', value = 'FragmentedMessageWebSocketSpec')
    @ServerWebSocket("/fragmented/undecodable")
    static class UndecodableServerWebSocket {
        @OnMessage
        @Consumes('application/x-undecodable')
        Publisher<String> onMessage(Message message, WebSocketSession session) {
            return session.send(message.text)
        }
    }
}
