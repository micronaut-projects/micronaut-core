package io.micronaut.http.client.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.exceptions.WebSocketClientException
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import io.netty.util.ReferenceCountUtil
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ClientWebsocketSpec extends Specification {
    void 'websocket bean should not open if there is a connection error'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ClientWebsocketSpec'])
        def client = ctx.getBean(WebSocketClient)
        def registry = ctx.getBean(ClientBeanRegistry)
        def mono = Mono.from(client.connect(ClientBean.class, 'http://does-not-exist'))

        when:
        mono.toFuture().get()
        then:
        def e = thrown ExecutionException
        e.cause instanceof WebSocketClientException

        registry.clientBeans.size() == 1
        !registry.clientBeans[0].opened
        !registry.clientBeans[0].autoClosed
        !registry.clientBeans[0].onClosed

        cleanup:
        client.close()
    }

    void 'client echoes a close frame from the server and closes the connection'() {
        given:
        BlockingQueue<Object> received = new LinkedBlockingQueue<>()
        CompletableFuture<Void> connectionClosed = new CompletableFuture<>()
        def group = new NioEventLoopGroup(1)
        Channel serverChannel = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.closeFuture().addListener { connectionClosed.complete(null) }
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(4096))
                                .addLast(new SimpleChannelInboundHandler<Object>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx_, Object msg) throws Exception {
                                        if (msg instanceof FullHttpRequest) {
                                            def handshaker = new WebSocketServerHandshakerFactory('ws://localhost/', null, false)
                                                    .newHandshaker(msg)
                                            handshaker.handshake(ctx_.channel(), msg).addListener {
                                                // send a close frame, but leave closing the connection to the client
                                                ctx_.writeAndFlush(new CloseWebSocketFrame(1001, 'going away'))
                                            }
                                        } else {
                                            received.add(ReferenceCountUtil.retain(msg))
                                        }
                                    }
                                })
                    }
                })
                .bind('127.0.0.1', 0).sync().channel()
        def port = ((InetSocketAddress) serverChannel.localAddress()).port
        def ctx = ApplicationContext.run(['spec.name': 'ClientWebsocketSpec'])
        def client = ctx.getBean(WebSocketClient)
        def registry = ctx.getBean(ClientBeanRegistry)

        when:
        Mono.from(client.connect(ClientBean.class, "http://127.0.0.1:$port/")).toFuture().get(10, TimeUnit.SECONDS)
        def echo = received.poll(10, TimeUnit.SECONDS)

        then:
        echo instanceof CloseWebSocketFrame
        ((CloseWebSocketFrame) echo).statusCode() == 1001
        connectionClosed.get(10, TimeUnit.SECONDS) == null
        registry.clientBeans.count { it.onClosed } == 1

        cleanup:
        ReferenceCountUtil.release(echo)
        received.each { ReferenceCountUtil.release(it) }
        client.close()
        ctx.close()
        serverChannel.close().sync()
        group.shutdownGracefully()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ClientWebsocketSpec')
    static class ClientBeanRegistry {
        List<ClientBean> clientBeans = new ArrayList<>()
    }

    @ClientWebSocket
    static class ClientBean implements AutoCloseable {
        boolean opened = false
        volatile boolean onClosed = false
        boolean autoClosed = false

        @Inject
        ClientBean(ClientBeanRegistry registry) {
            registry.clientBeans.add(this)
        }

        @OnOpen
        void open() {
            opened = true
        }

        @OnMessage
        void onMessage(String text) {
        }

        @OnClose
        void onClose() {
            onClosed = true
        }

        @Override
        void close() throws Exception {
            autoClosed = true
        }
    }
}
