package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The WebSockets of {@link ServerWebSocket} beans and of functional routes are under
 * {@code micronaut.server.context-path}, like the controllers.
 */
class WebSocketContextPathSpec extends Specification {

    private static final String SPEC = 'WebSocketContextPathSpec'

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'spec.name'                  : SPEC,
            'micronaut.server.port'      : -1,
            'micronaut.server.context-path': '/cp',
    ])

    @Shared
    EmbeddedServer server = context.getBean(EmbeddedServer).start()

    @Shared
    HttpClient http = HttpClient.newHttpClient()

    def cleanupSpec() {
        http.shutdownNow()
    }

    void 'a #kind WebSocket is under the context path'(String kind, String path) {
        given:
        def listener = new Listener()
        def ws = http.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}/cp${path}"), listener)
                .get(10, TimeUnit.SECONDS)

        when:
        ws.sendText('hello', true).get(10, TimeUnit.SECONDS)

        then:
        listener.messages.poll(10, TimeUnit.SECONDS) == "${kind} hello"

        cleanup:
        ws?.sendClose(WebSocket.NORMAL_CLOSURE, 'done')?.get(10, TimeUnit.SECONDS)

        where:
        kind         | path
        'annotated'  | '/annws'
        'functional' | '/fnws'
    }

    void 'a #kind WebSocket is not upgraded without the context path'(String kind, String path) {
        when:
        http.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}${path}"), new Listener())
                .get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof WebSocketHandshakeException
        ((WebSocketHandshakeException) e.cause).response.statusCode() == 404

        where:
        kind         | path
        'annotated'  | '/annws'
        'functional' | '/fnws'
    }

    static class Listener implements WebSocket.Listener {
        final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>()
        private final StringBuilder text = new StringBuilder()

        @Override
        CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data)
            if (last) {
                messages.add(text.toString())
                text.setLength(0)
            }
            webSocket.request(1)
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @ServerWebSocket('/annws')
    static class AnnotatedWebSocket {
        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync("annotated ${message}".toString())
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Factory
    static class FunctionalWebSockets {
        @Singleton
        HttpRoutes functionalWebSocket() {
            return { routes ->
                routes.GET('/fnws').webSocket { ws ->
                    ws.onMessage(String, { session, message -> session.sendAsync("functional ${message}".toString()) })
                }
            } as HttpRoutes
        }
    }
}
