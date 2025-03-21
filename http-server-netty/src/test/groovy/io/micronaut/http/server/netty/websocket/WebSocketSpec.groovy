package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.HttpFilter
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class WebSocketSpec extends Specification {
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/7920')
    def 'race condition with channel close from http filter'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'WebSocketSpec',
        ])
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)
        def delayingFilter = ctx.getBean(DelayingFilter)

        def serverEmbeddedChannel = embeddedServer.buildEmbeddedChannel(false)
        def clientEmbeddedChannel = new EmbeddedChannel()

        EmbeddedTestUtil.connect(serverEmbeddedChannel, clientEmbeddedChannel)

        def handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                URI.create('http://localhost/WebSocketSpec'),
                WebSocketVersion.V13,
                null,
                false,
                new DefaultHttpHeaders()
        )
        clientEmbeddedChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(4096))

        when:
        // send handshake to server
        handshaker.handshake(clientEmbeddedChannel)
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        // kill connection
        serverEmbeddedChannel.close()
        clientEmbeddedChannel.close()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        // finish delaying filter
        delayingFilter.delay.tryEmitEmpty()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)

        then:
        !handshaker.isHandshakeComplete()

        cleanup:
        clientEmbeddedChannel.close()
        serverEmbeddedChannel.close()
        ctx.close()
    }

    @ServerWebSocket('/WebSocketSpec')
    @Requires(property = 'spec.name', value = 'WebSocketSpec')
    static class Socket {
        @OnMessage
        def onMessage(String message, WebSocketSession session) {
            return session.send('reply: ' + message)
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketSpec')
    @Singleton
    @Filter('/WebSocketSpec')
    static class DelayingFilter implements HttpFilter {
        Sinks.Empty<HttpResponse<?>> delay

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
            delay = Sinks.empty()
            return Flux.concat(delay.asMono(), Flux.defer(() -> chain.proceed(request)))
        }
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/11506')
    def 'netty WebSocketFrame'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'WebSocketSpec2',
        ])
        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = ctx.createBean(WebSocketClient, embeddedServer.URI)

        when:
        def sock = Mono.from(client.connect(ClientSocket, "/ServerSocket")).block()
        sock.send("foo")
        then:
        sock.reply.get() == "reply: foo"

        cleanup:
        sock.close()
        client.close()
        embeddedServer.close()
        ctx.close()
    }

    @ServerWebSocket('/ServerSocket')
    @Requires(property = 'spec.name', value = 'WebSocketSpec2')
    static class ServerSocket {
        @OnMessage
        def onMessage(WebSocketFrame message, WebSocketSession session) {
            def text = ((TextWebSocketFrame) message).text()
            message.release()
            return session.send('reply: ' + text)
        }
    }

    @ClientWebSocket
    static abstract class ClientSocket implements AutoCloseable {
        def reply = new CompletableFuture<String>()

        abstract void send(String message);

        @OnMessage
        public void onMessage(String message) {
            reply.complete(message)
        }
    }
}
