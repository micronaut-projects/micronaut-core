package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.HttpFilter
import io.micronaut.http.netty.websocket.NettyWebSocketSession
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.ReferenceCountUtil
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.channels.ClosedChannelException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebSocketSpec extends Specification {
    def 'oversized fragmented message closes connection without onClose handler'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'WebSocketPayloadLimitSpec',
        ])
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)
        def serverEmbeddedChannel = embeddedServer.buildEmbeddedChannel(false)
        def clientEmbeddedChannel = new EmbeddedChannel()
        EmbeddedTestUtil.connect(serverEmbeddedChannel, clientEmbeddedChannel)

        def handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                URI.create('http://localhost/payload-limit'),
                WebSocketVersion.V13,
                null,
                false,
                new DefaultHttpHeaders()
        )
        clientEmbeddedChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(4096))

        when:
        handshaker.handshake(clientEmbeddedChannel)
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        def handshakeResponse = (FullHttpResponse) clientEmbeddedChannel.readInbound()
        try {
            handshaker.finishHandshake(clientEmbeddedChannel, handshakeResponse)
        } finally {
            handshakeResponse.release()
        }
        clientEmbeddedChannel.writeOutbound(new TextWebSocketFrame(false, 0, '123'))
        clientEmbeddedChannel.writeOutbound(new ContinuationWebSocketFrame(false, 0, '456'))
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)

        then:
        def closeFrame = (CloseWebSocketFrame) clientEmbeddedChannel.readInbound()
        closeFrame.statusCode() == 1009
        !ctx.getBean(PayloadLimitSocket).session.isOpen()
        !serverEmbeddedChannel.isOpen()

        cleanup:
        closeFrame?.release()
        clientEmbeddedChannel.finishAndReleaseAll()
        serverEmbeddedChannel.finishAndReleaseAll()
        ctx.close()
    }

    private static List<EmbeddedChannel> connectWebSocket(ApplicationContext ctx, String path) {
        def embeddedServer = (NettyHttpServer) ctx.getBean(EmbeddedServer)
        def serverEmbeddedChannel = embeddedServer.buildEmbeddedChannel(false)
        def clientEmbeddedChannel = new EmbeddedChannel()
        EmbeddedTestUtil.connect(serverEmbeddedChannel, clientEmbeddedChannel)

        def handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                URI.create('http://localhost' + path),
                WebSocketVersion.V13,
                null,
                false,
                new DefaultHttpHeaders()
        )
        clientEmbeddedChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(4096))
        handshaker.handshake(clientEmbeddedChannel)
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        def handshakeResponse = (FullHttpResponse) clientEmbeddedChannel.readInbound()
        try {
            handshaker.finishHandshake(clientEmbeddedChannel, handshakeResponse)
        } finally {
            handshakeResponse.release()
        }
        return [serverEmbeddedChannel, clientEmbeddedChannel]
    }

    def 'connection is closed after close frame, #trigger, onClose handler: #withOnClose'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'WebSocketCloseSpec',
        ])
        def (EmbeddedChannel serverEmbeddedChannel, EmbeddedChannel clientEmbeddedChannel) =
                connectWebSocket(ctx, withOnClose ? '/close/on-close' : '/close/no-on-close')

        when:
        switch (trigger) {
            case 'idle':
                serverEmbeddedChannel.pipeline().fireUserEventTriggered(IdleStateEvent.ALL_IDLE_STATE_EVENT)
                break
            case 'handler error':
                clientEmbeddedChannel.writeOutbound(new TextWebSocketFrame('foo'))
                break
            case 'session close':
                clientEmbeddedChannel.writeOutbound(new TextWebSocketFrame('close'))
                break
            case 'client close':
                clientEmbeddedChannel.writeOutbound(new CloseWebSocketFrame(1000, 'bye'))
                break
            case 'client close without status':
                clientEmbeddedChannel.writeOutbound(new CloseWebSocketFrame())
                break
        }
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)
        def closeFrame = (CloseWebSocketFrame) clientEmbeddedChannel.readInbound()
        def extraFrame = clientEmbeddedChannel.readInbound()
        def onCloseSocket = ctx.getBean(OnCloseSocket)

        then:
        closeFrame.statusCode() == expectedCode
        extraFrame == null
        !serverEmbeddedChannel.isOpen()
        // @OnClose is called exactly once, sees the session as closed, and its (never completing)
        // publisher does not delay closing the connection
        onCloseSocket.reasons.collect { it.code } == (withOnClose ? [expectedCode] : [])
        onCloseSocket.openInOnClose == (withOnClose ? [false] : [])

        cleanup:
        closeFrame?.release()
        clientEmbeddedChannel.finishAndReleaseAll()
        serverEmbeddedChannel.finishAndReleaseAll()
        ctx.close()

        where:
        trigger                       | withOnClose | expectedCode
        'idle'                        | false       | CloseReason.GOING_AWAY.code
        'idle'                        | true        | CloseReason.GOING_AWAY.code
        'handler error'               | false       | CloseReason.INTERNAL_ERROR.code
        'handler error'               | true        | CloseReason.INTERNAL_ERROR.code
        'session close'               | false       | CloseReason.NORMAL.code
        'session close'               | true        | CloseReason.NORMAL.code
        'client close'                | false       | 1000
        'client close'                | true        | 1000
        'client close without status' | false       | -1
        'client close without status' | true        | -1
    }

    def 'connection is force closed if the peer does not read the close frame, onClose handler: #withOnClose'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name': 'WebSocketCloseSpec',
        ])
        def (EmbeddedChannel serverEmbeddedChannel, EmbeddedChannel clientEmbeddedChannel) =
                connectWebSocket(ctx, withOnClose ? '/close/on-close' : '/close/no-on-close')
        // simulate a peer that does not read: writes only complete (fail) when the channel is closed
        List<Object> heldWrites = []
        List<ChannelPromise> heldPromises = []
        serverEmbeddedChannel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            void write(ChannelHandlerContext ctx_, Object msg, ChannelPromise promise) throws Exception {
                heldWrites.add(msg)
                heldPromises.add(promise)
            }
        })
        serverEmbeddedChannel.closeFuture().addListener {
            heldPromises.each { it.tryFailure(new ClosedChannelException()) }
        }
        def session = serverEmbeddedChannel.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY).get()

        when:
        serverEmbeddedChannel.pipeline().fireUserEventTriggered(IdleStateEvent.ALL_IDLE_STATE_EVENT)
        // neither further idle events nor session.close() write another close frame
        serverEmbeddedChannel.pipeline().fireUserEventTriggered(IdleStateEvent.ALL_IDLE_STATE_EVENT)
        session.close(CloseReason.NORMAL)
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)

        then:
        heldWrites.size() == 1
        !session.isOpen()
        serverEmbeddedChannel.isOpen()

        when:
        serverEmbeddedChannel.advanceTimeBy(1, TimeUnit.SECONDS)
        serverEmbeddedChannel.runScheduledPendingTasks()
        EmbeddedTestUtil.advance(serverEmbeddedChannel, clientEmbeddedChannel)

        then:
        !serverEmbeddedChannel.isOpen()
        heldWrites.size() == 1
        ctx.getBean(OnCloseSocket).reasons.collect { it.code } == (withOnClose ? [CloseReason.GOING_AWAY.code] : [])

        cleanup:
        heldWrites.each { ReferenceCountUtil.release(it) }
        clientEmbeddedChannel.finishAndReleaseAll()
        serverEmbeddedChannel.finishAndReleaseAll()
        ctx.close()

        where:
        withOnClose << [false, true]
    }

    def 'session close from outside the event loop closes the connection'() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name'            : 'WebSocketRemoteCloseSpec',
                'micronaut.server.port': -1,
        ])
        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()
        def wsClient = ctx.createBean(WebSocketClient, embeddedServer.URI)
        def server = ctx.getBean(RemoteCloseServerSocket)
        def conditions = new PollingConditions(timeout: 10)

        when:
        def client = Mono.from(wsClient.connect(RemoteCloseClientSocket, '/close/remote')).block(Duration.ofSeconds(10))
        conditions.eventually {
            assert server.session != null
        }
        // called from the test thread, not the event loop
        server.session.close(CloseReason.GOING_AWAY)

        then:
        !server.session.isOpen()
        conditions.eventually {
            assert server.reasons.collect { it.code } == [CloseReason.GOING_AWAY.code]
            assert client.reasons.collect { it.code } == [CloseReason.GOING_AWAY.code]
        }

        cleanup:
        wsClient?.close()
        ctx.close()
    }

    @ServerWebSocket('/close/remote')
    @Requires(property = 'spec.name', value = 'WebSocketRemoteCloseSpec')
    static class RemoteCloseServerSocket {
        volatile WebSocketSession session
        List<CloseReason> reasons = new CopyOnWriteArrayList<>()

        @OnOpen
        void onOpen(WebSocketSession session) {
            this.session = session
        }

        @OnMessage
        void onMessage(String message) {
        }

        @OnClose
        void onClose(CloseReason reason) {
            reasons.add(reason)
        }
    }

    @ClientWebSocket
    @Requires(property = 'spec.name', value = 'WebSocketRemoteCloseSpec')
    static class RemoteCloseClientSocket {
        List<CloseReason> reasons = new CopyOnWriteArrayList<>()

        @OnMessage
        void onMessage(String message) {
        }

        @OnClose
        void onClose(CloseReason reason) {
            reasons.add(reason)
        }
    }

    @ServerWebSocket('/close/no-on-close')
    @Requires(property = 'spec.name', value = 'WebSocketCloseSpec')
    static class NoOnCloseSocket {
        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            if (message == 'close') {
                session.close(CloseReason.NORMAL)
            } else {
                throw new RuntimeException('expected')
            }
        }
    }

    @ServerWebSocket('/close/on-close')
    @Requires(property = 'spec.name', value = 'WebSocketCloseSpec')
    static class OnCloseSocket {
        List<CloseReason> reasons = []
        List<Boolean> openInOnClose = []

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            if (message == 'close') {
                session.close(CloseReason.NORMAL)
            } else {
                throw new RuntimeException('expected')
            }
        }

        @OnClose
        Publisher<?> onClose(CloseReason reason, WebSocketSession session) {
            reasons.add(reason)
            openInOnClose.add(session.isOpen())
            return Mono.never()
        }
    }

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

    @ServerWebSocket('/payload-limit')
    @Requires(property = 'spec.name', value = 'WebSocketPayloadLimitSpec')
    static class PayloadLimitSocket {
        WebSocketSession session

        @OnOpen
        void onOpen(WebSocketSession session) {
            this.session = session
        }

        @OnMessage(maxPayloadLength = 4)
        void onMessage(String message) {
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
