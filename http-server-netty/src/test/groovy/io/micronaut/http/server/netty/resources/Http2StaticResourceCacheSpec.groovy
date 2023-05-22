package io.micronaut.http.server.netty.resources

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpHeaders
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.codec.http2.Http2Settings
import io.netty.handler.codec.http2.HttpConversionUtil
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SupportedCipherSuiteFilter
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import spock.lang.Specification

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture

class Http2StaticResourceCacheSpec extends Specification {
    def 'response should have proper stream id'() {
        given:
        def tempFile = File.createTempFile("Http2StaticResourceCacheSpec", ".html")
        tempFile.write("<html><head></head><body>HTML Page from static file</body></html>")
        def tempSubDir = new File(tempFile.getParentFile(), "doesntexist")
        def app = ApplicationContext.run([
                'micronaut.server.http-version': '2.0',
                'micronaut.ssl.enabled': true,
                'micronaut.server.ssl.port': -1,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.router.static-resources.default.paths': ['classpath:public', 'file:' + tempFile.parent, 'file:' + tempSubDir.absolutePath]
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
                        ApplicationProtocolNames.HTTP_2))
                .build()
        def completion = new CompletableFuture<HttpResponse>()
        def bootstrap = new Bootstrap()
                .remoteAddress(embeddedServer.host, embeddedServer.port)
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(@NonNull SocketChannel ch) throws Exception {
                        def connection = new DefaultHttp2Connection(false)
                        def connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
                                .initialSettings(Http2Settings.defaultSettings())
                                .frameListener(new InboundHttp2ToHttpAdapterBuilder(connection)
                                                .maxContentLength(Integer.MAX_VALUE)
                                                .propagateSettings(false)
                                                .build())
                                .connection(connection)
                                .build()
                        ch.pipeline()
                                .addLast(sslContext.newHandler(ch.alloc(), embeddedServer.host, embeddedServer.port))
                                .addLast(new ApplicationProtocolNegotiationHandler('') {
                                    @Override
                                    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
                                        if (ApplicationProtocolNames.HTTP_2 != protocol) {
                                            throw new AssertionError((Object) protocol)
                                        }
                                        ctx.pipeline()
                                                .addLast(connectionHandler)
                                                .addLast(new SimpleChannelInboundHandler<HttpResponse>() {
                                                    @Override
                                                    protected void channelRead0(ChannelHandlerContext ctx_, HttpResponse msg) throws Exception {
                                                        completion.complete(msg)
                                                    }
                                                })


                                        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/' + tempFile.getName())
                                        request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), 'https')
                                        request.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3)
                                        request.headers().add(HttpHeaders.IF_MODIFIED_SINCE, Instant.ofEpochMilli(tempFile.lastModified()).atZone(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME))
                                        ctx.channel().writeAndFlush(request)
                                    }
                                })
                    }
                })

        when:
        def channel = bootstrap.connect().await().channel()
        def response = completion.get()
        then:
        response.status() == HttpResponseStatus.NOT_MODIFIED
        response.headers().get(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text()) == '3'

        cleanup:
        tempFile.delete()
        channel.close()
        embeddedServer.close()
    }
}
