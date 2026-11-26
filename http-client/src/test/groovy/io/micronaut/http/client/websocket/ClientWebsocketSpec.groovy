package io.micronaut.http.client.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.netty.NettyClientCustomizer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.exceptions.WebSocketClientException
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
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
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.util.ReferenceCountUtil
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification

import javax.net.ssl.SSLHandshakeException
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

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/10744")
    void 'websocket bean can connect to echo server over SSL with wss scheme'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ClientWebsocketSpec'])
        def client = ctx.getBean(WebSocketClient)
        def registry = ctx.getBean(ClientBeanRegistry)
        def mono = Mono.from(client.connect(ClientBean.class, 'wss://websocket-echo.com'))

        when:
        mono.toFuture().get()

        then:
        registry.clientBeans.size() == 1
        registry.clientBeans[0].opened
        !registry.clientBeans[0].autoClosed
        !registry.clientBeans[0].onClosed

        cleanup:
        client.close()
    }

    void 'websocket bean can connect to echo server over SSL with https scheme'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ClientWebsocketSpec'])//, "micronaut.http.client.alpn-modes":"http/1.1"])
        def client = ctx.getBean(WebSocketClient)
        def registry = ctx.getBean(ClientBeanRegistry)
        def mono = Mono.from(client.connect(ClientBean.class, 'https://websocket-echo.com'))

        when:
        mono.toFuture().get()

        then:
        registry.clientBeans.size() == 1
        registry.clientBeans[0].opened
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

    void 'server closing the connection during the TLS handshake reports the cause'() {
        given:
        CompletableFuture<Void> connectionClosed = new CompletableFuture<>()
        def group = new NioEventLoopGroup(1)
        Channel serverChannel = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.closeFuture().addListener { connectionClosed.complete(null) }
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            void channelRead(ChannelHandlerContext ctx_, Object msg) throws Exception {
                                // close instead of answering the client hello
                                ReferenceCountUtil.release(msg)
                                ctx_.close()
                            }
                        })
                    }
                })
                .bind('127.0.0.1', 0).sync().channel()
        def port = ((InetSocketAddress) serverChannel.localAddress()).port
        def ctx = ApplicationContext.run([
                'spec.name': 'ClientWebsocketSpec',
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def client = ctx.getBean(WebSocketClient)
        def registry = ctx.getBean(ClientBeanRegistry)

        when:
        Mono.from(client.connect(ClientBean.class, "wss://127.0.0.1:$port/")).toFuture().get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof WebSocketClientException
        e.cause.message == 'Error opening WebSocket client session: TLS handshake failed'
        e.cause.cause != null
        connectionClosed.get(10, TimeUnit.SECONDS) == null
        registry.clientBeans.every { !it.opened }

        cleanup:
        client.close()
        ctx.close()
        serverChannel.close().sync()
        group.shutdownGracefully()
    }

    void 'an untrusted server certificate fails the connect before the upgrade is sent'() {
        given: 'a TLS server with a self-signed certificate that records the requests it receives'
        def certificate = new SelfSignedCertificate()
        def serverSsl = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build()
        BlockingQueue<String> requests = new LinkedBlockingQueue<>()
        def group = new NioEventLoopGroup(1)
        Channel serverChannel = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(serverSsl.newHandler(ch.alloc()))
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(4096))
                                .addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx_, FullHttpRequest msg) throws Exception {
                                        requests.add(msg.uri())
                                    }

                                    @Override
                                    void exceptionCaught(ChannelHandlerContext ctx_, Throwable cause) throws Exception {
                                        // the client rejects the certificate
                                        ctx_.close()
                                    }
                                })
                    }
                })
                .bind('127.0.0.1', 0).sync().channel()
        def port = ((InetSocketAddress) serverChannel.localAddress()).port
        // the default trust store does not trust the self-signed certificate
        def ctx = ApplicationContext.run(['spec.name': 'ClientWebsocketSpec'])
        def client = ctx.getBean(WebSocketClient)
        def registry = ctx.getBean(ClientBeanRegistry)

        when:
        Mono.from(client.connect(ClientBean.class, HttpRequest.GET("wss://127.0.0.1:$port/").bearerAuth('secret-token')))
                .toFuture().get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof WebSocketClientException
        e.cause.message == 'Error opening WebSocket client session: TLS handshake failed'
        e.cause.cause instanceof SSLHandshakeException
        requests.poll(200, TimeUnit.MILLISECONDS) == null
        registry.clientBeans.every { !it.opened }

        cleanup:
        client.close()
        ctx.close()
        serverChannel.close().sync()
        group.shutdownGracefully()
        certificate.delete()
    }

    void 'a wss connect without an SslHandler never sends the upgrade in plaintext'() {
        given: 'a server that records every byte it receives'
        ServerSocket target = new ServerSocket(0, 50, InetAddress.loopbackAddress)
        ByteArrayOutputStream received = new ByteArrayOutputStream()
        CompletableFuture<Void> connectionClosed = new CompletableFuture<>()
        Thread.startDaemon('websocket-plaintext-target') {
            try {
                target.accept().withCloseable { Socket socket -> received << socket.inputStream }
            } catch (IOException ignored) {
                // the target was closed
            } finally {
                connectionClosed.complete(null)
            }
        }
        def ctx = ApplicationContext.run([
                'spec.name': 'ClientWebsocketSpec',
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        // e.g. a customizer that removes the SslHandler from the pipeline
        ctx.getBean(NettyClientCustomizer.Registry).register(new NettyClientCustomizer() {
            @Override
            NettyClientCustomizer specializeForChannel(Channel channel, NettyClientCustomizer.ChannelRole role) {
                return new NettyClientCustomizer() {
                    @Override
                    void onInitialPipelineBuilt() {
                        def ssl = channel.pipeline().get(SslHandler)
                        if (ssl != null) {
                            channel.pipeline().remove(ssl)
                        }
                    }
                }
            }
        })
        def client = ctx.getBean(WebSocketClient)
        def registry = ctx.getBean(ClientBeanRegistry)

        when:
        Mono.from(client.connect(ClientBean.class, HttpRequest.GET("wss://127.0.0.1:${target.localPort}/").bearerAuth('secret-token')))
                .toFuture().get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof WebSocketClientException
        e.cause.message.contains('a wss connection must use TLS')
        connectionClosed.get(10, TimeUnit.SECONDS) == null
        received.size() == 0
        registry.clientBeans.every { !it.opened }

        cleanup:
        client.close()
        ctx.close()
        target.close()
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
