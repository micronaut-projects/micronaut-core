package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.jetbrains.annotations.NotNull
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit

class BroadcasterSpec extends Specification {
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6269')
    def 'closing channels simultaneously does not log an error'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name'                            : 'BroadcasterSpec',
                'micronaut.server.netty.worker.threads': 1,
                'micronaut.http.client.num-of-threads' : 1,
                'micronaut.server.port'                : -1
        ])
        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()

        def uri = "ws://$embeddedServer.host:$embeddedServer.port/ws"

        def channels = new ArrayList<Channel>()
        def handshakes = new ArrayList<ChannelFuture>()
        def eventLoopGroup = new NioEventLoopGroup(1)
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(@NotNull SocketChannel ch) throws Exception {
                        def handshaker = WebSocketClientHandshakerFactory.newHandshaker(new URI(uri), WebSocketVersion.V13, null, true, new DefaultHttpHeaders())
                        def handshakeFuture = ch.newPromise()
                        handshakes.add(handshakeFuture)
                        ch.pipeline()
                            .addLast(new HttpClientCodec())
                            .addLast(new HttpObjectAggregator(1024))
                            .addLast(new SimpleChannelInboundHandler<Object>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx_, Object msg) throws Exception {
                                    if (!handshaker.isHandshakeComplete()) {
                                        handshaker.finishHandshake(ctx_.channel(), (FullHttpResponse) msg)
                                        handshakeFuture.setSuccess()
                                    }
                                }

                                @Override
                                void channelActive(@NotNull ChannelHandlerContext ctx_) throws Exception {
                                    handshaker.handshake(ctx_.channel())
                                }
                            })
                        channels.add(ch)
                    }
                })
                .remoteAddress(embeddedServer.host, embeddedServer.port)

        for (int i = 0; i < 3; i++) {
            bootstrap.connect().sync()
        }
        for (def handshake : handshakes) {
            handshake.sync()
        }
        eventLoopGroup.execute {
            for (Channel channel : channels) {
                channel.writeAndFlush(new CloseWebSocketFrame(WebSocketCloseStatus.ENDPOINT_UNAVAILABLE))
                channel.close()
            }
        }
        for (Channel channel : channels) {
            channel.closeFuture().sync()
        }

        def server = ctx.getBean(Server)
        server.connectionPhaser.awaitAdvanceInterruptibly(server.connectionPhaser.arrive(), 10, TimeUnit.SECONDS)

        expect:
        server.errors.isEmpty()

        cleanup:
        ctx.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'BroadcasterSpec')
    @ServerWebSocket("/ws")
    static class Server {
        @Inject
        WebSocketBroadcaster broadcaster
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>())
        Phaser connectionPhaser = new Phaser(1) // one party for waiting

        @OnMessage
        def onMessage(String msg) {}

        @OnClose
        def onClose() {
            connectionPhaser.register()
            broadcaster.broadcast('foo').subscribe(new Subscriber<String>() {
                @Override
                void onSubscribe(Subscription s) {
                    s.request(Long.MAX_VALUE)
                }

                @Override
                void onNext(String s) {
                }

                @Override
                void onError(Throwable t) {
                    t.printStackTrace()
                    errors.add(t)
                    connectionPhaser.arrive()
                }

                @Override
                void onComplete() {
                    connectionPhaser.arrive()
                }
            })
        }
    }
}
