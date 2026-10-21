package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import io.micronaut.http.annotation.Header
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class WebSocketCloseAnswerSpec extends Specification {

    ApplicationContext ctx
    EmbeddedChannel server
    EmbeddedChannel client

    def setup() {
        ctx = ApplicationContext.run(['spec.name': 'WebSocketCloseAnswerSpec'])
        server = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        client = new EmbeddedChannel()
        EmbeddedTestUtil.connect(server, client)
    }

    def cleanup() {
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        ctx.close()
    }

    void 'the close of the peer is answered once and the channel closed'() {
        given:
        handshake('/close-answer/ok')

        when:
        closeFromClient()

        then:
        answers() == [1000]
        !server.isOpen()
        ctx.getBean(CloseAnswerSocket).closes == ['ok']
    }

    void 'a failed async close handler is not logged when error logging is off and still answers the close'() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger(NettyServerWebSocketHandler)
        Level level = logger.level
        logger.level = Level.OFF
        handshake('/close-answer/fail-async')

        when:
        closeFromClient()

        then:
        answers() == [1000]
        !server.isOpen()
        ctx.getBean(CloseAnswerSocket).closes == ['fail-async']

        cleanup:
        logger.level = level
    }

    void 'a failed async close handler still answers the close and closes the channel'() {
        given:
        handshake('/close-answer/fail-async')

        when:
        closeFromClient()

        then:
        answers() == [1000]
        !server.isOpen()
        ctx.getBean(CloseAnswerSocket).closes == ['fail-async']
    }

    void 'a throwing close handler still answers the close and closes the channel'() {
        given:
        handshake('/close-answer/fail-sync')

        when:
        closeFromClient()

        then:
        answers() == [1000]
        !server.isOpen()
        ctx.getBean(CloseAnswerSocket).closes == ['fail-sync']
    }

    void 'a failed write of the close answer still closes the channel'() {
        given:
        handshake('/close-answer/ok')
        server.pipeline().addBefore(NettyServerWebSocketHandler.ID, 'fail-close', new ChannelOutboundHandlerAdapter() {
            @Override
            void write(ChannelHandlerContext context, Object msg, ChannelPromise promise) {
                if (msg instanceof CloseWebSocketFrame) {
                    ReferenceCountUtil.release(msg)
                    promise.setFailure(new IOException('write failed'))
                } else {
                    context.write(msg, promise)
                }
            }
        })

        when:
        closeFromClient()

        then:
        answers() == []
        !server.isOpen()
        ctx.getBean(CloseAnswerSocket).closes == ['ok']
    }

    void 'a failed write of the close answer is logged at debug level and still closes the channel'() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger(NettyServerWebSocketHandler)
        Level level = logger.level
        logger.level = Level.DEBUG
        handshake('/close-answer/ok')
        server.pipeline().addBefore(NettyServerWebSocketHandler.ID, 'fail-close', new ChannelOutboundHandlerAdapter() {
            @Override
            void write(ChannelHandlerContext context, Object msg, ChannelPromise promise) {
                if (msg instanceof CloseWebSocketFrame) {
                    ReferenceCountUtil.release(msg)
                    promise.setFailure(new IOException('write failed'))
                } else {
                    context.write(msg, promise)
                }
            }
        })

        when:
        closeFromClient()

        then:
        answers() == []
        !server.isOpen()
        ctx.getBean(CloseAnswerSocket).closes == ['ok']

        cleanup:
        logger.level = level
    }

    void 'a close handler that cannot be bound still answers the close and closes the channel'() {
        given:
        handshake('/close-answer-unbound')

        when:
        closeFromClient()

        then:
        answers() == [1000]
        !server.isOpen()
    }

    private void handshake(String path) {
        def handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                URI.create('http://localhost' + path), WebSocketVersion.V13, null, false, new DefaultHttpHeaders())
        client.pipeline().addLast(new HttpClientCodec()).addLast(new HttpObjectAggregator(4096))
        handshaker.handshake(client)
        EmbeddedTestUtil.advance(server, client)
        def response = (FullHttpResponse) client.readInbound()
        try {
            handshaker.finishHandshake(client, response)
        } finally {
            response.release()
        }
    }

    private void closeFromClient() {
        client.writeOutbound(new CloseWebSocketFrame(1000, 'bye'))
        EmbeddedTestUtil.advance(server, client)
    }

    private List<Integer> answers() {
        List<Integer> codes = []
        Object msg
        while ((msg = client.readInbound()) != null) {
            if (msg instanceof CloseWebSocketFrame) {
                codes << msg.statusCode()
            }
            ReferenceCountUtil.release(msg)
        }
        return codes
    }

    @ServerWebSocket('/close-answer/{mode}')
    @Requires(property = 'spec.name', value = 'WebSocketCloseAnswerSpec')
    static class CloseAnswerSocket {
        final List<String> closes = []

        @OnMessage
        void onMessage(String message) {
            // the specs only exercise the close of the session
        }

        @OnClose
        CompletableFuture<Void> onClose(String mode, CloseReason reason) {
            closes << mode
            if (mode == 'fail-sync') {
                throw new IllegalStateException('close handler failed')
            }
            if (mode == 'fail-async') {
                return CompletableFuture.failedFuture(new IllegalStateException('close stage failed'))
            }
            return CompletableFuture.completedFuture(null)
        }
    }

    @ServerWebSocket('/close-answer-unbound')
    @Requires(property = 'spec.name', value = 'WebSocketCloseAnswerSpec')
    static class UnboundCloseSocket {

        @OnMessage
        void onMessage(String message) {
            // the spec only exercises the close of the session
        }

        @OnClose
        void onClose(CloseReason reason, @Header('X-Missing') String missing) {
            // never invoked: the header argument cannot be bound
        }
    }
}
