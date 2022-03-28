package io.micronaut.http.server.netty.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SupportedCipherSuiteFilter
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.jetbrains.annotations.NotNull
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class SslServerSpec extends Specification {
    def 'unsupported alpn protocol'() {
        given:
        def app = ApplicationContext.run([
                "micronaut.ssl.enabled": true,
                "micronaut.server.http-version": '2.0',
                "micronaut.server.ssl.buildSelfSigned": true,
                "micronaut.server.ssl.port": -1,
        ])
        def embeddedServer = app.getBean(EmbeddedServer)
        embeddedServer.start()

        def sslContext = SslContextBuilder.forClient()
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        'foo'))
                .build()
        def configuredProtocol = new CompletableFuture<String>()
        def bootstrap = new Bootstrap()
                .remoteAddress(embeddedServer.host, embeddedServer.port)
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(@NotNull SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(sslContext.newHandler(ch.alloc(), embeddedServer.host, embeddedServer.port))
                                .addLast(new ApplicationProtocolNegotiationHandler('fallback') {
                                    @Override
                                    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
                                        configuredProtocol.complete(protocol)
                                        ctx.read()
                                    }

                                    @Override
                                    protected void handshakeFailure(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                        configuredProtocol.completeExceptionally(cause)
                                    }
                                })
                    }
                })

        when:
        def channel = bootstrap.connect().await().channel()
        then:
        configuredProtocol.get() == 'fallback'

        cleanup:
        channel.close()
        embeddedServer.close()

    }
}
