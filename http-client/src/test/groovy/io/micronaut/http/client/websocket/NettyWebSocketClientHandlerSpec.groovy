package io.micronaut.http.client.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.RequestBinderRegistry
import io.micronaut.http.body.MessageBodyHandlerRegistry
import io.micronaut.http.client.netty.websocket.NettyWebSocketClientHandler
import io.micronaut.http.codec.MediaTypeCodecRegistry
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.context.WebSocketBeanRegistry
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.EmptyHttpHeaders
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import io.micronaut.http.client.exceptions.ReadTimeoutException
import java.time.Duration
import java.util.concurrent.TimeUnit
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The connect of the websocket client handler on an embedded channel, before any handshake
 * response arrives.
 */
class NettyWebSocketClientHandlerSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'NettyWebSocketClientHandlerSpec'])

    void 'a connect cancelled before the handler is added closes the channel once it is added'() {
        given:
        NettyWebSocketClientHandler<EmbeddedClient> handler = handler()

        when:
        handler.handshakeCompletedFlow.cancel()
        EmbeddedChannel channel = new EmbeddedChannel(handler)

        then: 'no handshake was written, and the channel is closed'
        channel.outboundMessages().isEmpty()
        !channel.open
    }

    void 'cancelling the connect after the handler was added closes the channel'() {
        given:
        NettyWebSocketClientHandler<EmbeddedClient> handler = handler()
        EmbeddedChannel channel = new EmbeddedChannel(handler)

        when:
        handler.handshakeCompletedFlow.cancel()

        then:
        !channel.open

        cleanup:
        channel.finishAndReleaseAll()
    }

    void 'a handshake that cannot be written fails the connect'() {
        given: 'a pipeline without an HTTP encoder, so that the handshake request cannot be sent'
        NettyWebSocketClientHandler<EmbeddedClient> handler = handler()

        when:
        EmbeddedChannel channel = new EmbeddedChannel(handler)

        then:
        handler.handshakeCompletedFlow.tryCompleteError() != null
        channel.open

        cleanup:
        channel.finishAndReleaseAll()
    }

    void 'a handshake response that does not arrive in time fails the connect with a read timeout'() {
        given:
        NettyWebSocketClientHandler<EmbeddedClient> handler = new NettyWebSocketClientHandler<EmbeddedClient>(
                HttpRequest.GET('/ws'),
                WebSocketBeanRegistry.forClient(ctx).getWebSocket(EmbeddedClient),
                WebSocketClientHandshakerFactory.newHandshaker(URI.create('ws://localhost/ws'), WebSocketVersion.V13, null, true, EmptyHttpHeaders.INSTANCE, 65536),
                ctx.getBean(RequestBinderRegistry),
                ctx.getBean(MediaTypeCodecRegistry),
                ctx.getBean(MessageBodyHandlerRegistry),
                ctx.getBean(ConversionService),
                Duration.ofSeconds(5))
        EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCodec())
        channel.pipeline().addLast(handler)

        expect: 'the handshake request was sent, and the connect waits for the response'
        channel.outboundMessages().size() > 0
        handler.handshakeCompletedFlow.tryComplete() == null

        when:
        channel.advanceTimeBy(5, TimeUnit.SECONDS)
        channel.runScheduledPendingTasks()

        then:
        handler.handshakeCompletedFlow.tryCompleteError() instanceof ReadTimeoutException
        !channel.open

        cleanup:
        channel.finishAndReleaseAll()
    }

    void 'closing an unclaimed endpoint whose handshake completed closes its session'() {
        given:
        NettyWebSocketClientHandler<EmbeddedClient> handler = handler()
        EmbeddedChannel channel = new EmbeddedChannel(new HttpClientCodec(), new HttpObjectAggregator(65536))
        channel.pipeline().addLast(handler)
        String request = channel.readOutbound().with { ByteBuf buf -> try { buf.toString(StandardCharsets.US_ASCII) } finally { buf.release() } }
        String key = request.readLines().find { it.toLowerCase().startsWith('sec-websocket-key:') }.substring('sec-websocket-key:'.length()).trim()
        String accept = Base64.encoder.encodeToString(MessageDigest.getInstance('SHA-1').digest((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').getBytes(StandardCharsets.US_ASCII)))

        when:
        channel.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n", StandardCharsets.US_ASCII))
        EmbeddedClient endpoint = handler.handshakeCompletedFlow.tryCompleteValue()

        then:
        endpoint.session.open

        when:
        handler.closeUnclaimed()

        then:
        !endpoint.session.open

        cleanup:
        channel.finishAndReleaseAll()
    }

    void 'closing an unclaimed endpoint without a session closes the channel'() {
        given:
        NettyWebSocketClientHandler<EmbeddedClient> handler = handler()
        EmbeddedChannel channel = new EmbeddedChannel(handler)

        when:
        handler.closeUnclaimed()

        then:
        !channel.open
        handler.clientEndpoint instanceof EmbeddedClient
    }

    void 'closing an unclaimed endpoint that was never added does nothing'() {
        given:
        NettyWebSocketClientHandler<EmbeddedClient> handler = handler()

        when:
        handler.closeUnclaimed()

        then:
        noExceptionThrown()
    }

    private NettyWebSocketClientHandler<EmbeddedClient> handler() {
        return new NettyWebSocketClientHandler<EmbeddedClient>(
                HttpRequest.GET('/ws'),
                WebSocketBeanRegistry.forClient(ctx).getWebSocket(EmbeddedClient),
                WebSocketClientHandshakerFactory.newHandshaker(URI.create('ws://localhost/ws'), WebSocketVersion.V13, null, true, EmptyHttpHeaders.INSTANCE, 65536),
                ctx.getBean(RequestBinderRegistry),
                ctx.getBean(MediaTypeCodecRegistry),
                ctx.getBean(MessageBodyHandlerRegistry),
                ctx.getBean(ConversionService))
    }

    @Requires(property = 'spec.name', value = 'NettyWebSocketClientHandlerSpec')
    @ClientWebSocket('/ws')
    static class EmbeddedClient implements AutoCloseable {
        WebSocketSession session

        @OnOpen
        void open(WebSocketSession session) {
            this.session = session
        }

        @OnMessage
        void onMessage(String text) {
            // no message is received on the embedded channel
        }

        @Override
        void close() {
            session?.close()
        }
    }
}
