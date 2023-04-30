package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.HttpFilter
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import spock.lang.Issue
import spock.lang.Specification

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
}
