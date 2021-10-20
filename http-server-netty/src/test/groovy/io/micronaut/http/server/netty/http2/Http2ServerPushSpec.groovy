package io.micronaut.http.server.netty.http2

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener
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
import jakarta.inject.Inject
import org.jetbrains.annotations.NotNull
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets

@MicronautTest
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.netty.log-level", value = "TRACE")
@Property(name = "micronaut.http.client.log-level", value = "TRACE")
@Property(name = "micronaut.ssl.enabled", value = "true")
@Property(name = "micronaut.ssl.port", value = "-1")
@Property(name = "micronaut.ssl.buildSelfSigned", value = "true")
@Property(name = "spec.name", value = "Http2ServerPushSpec")
class Http2ServerPushSpec extends Specification {
    @Inject
    EmbeddedServer embeddedServer

    private def request(boolean pushEnabled, String path) {
        int expectedResponses = pushEnabled ? 3 : 1
        def responses = new ArrayList()
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
        def bootstrap = new Bootstrap()
                .remoteAddress(embeddedServer.host, embeddedServer.port)
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(@NotNull SocketChannel ch) throws Exception {
                        def connection = new DefaultHttp2Connection(false)
                        def http2Settings = Http2Settings.defaultSettings()
                        http2Settings.pushEnabled(pushEnabled);
                        def connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
                                .initialSettings(http2Settings)
                                .frameListener(new DelegatingDecompressorFrameListener(
                                        connection,
                                        new InboundHttp2ToHttpAdapterBuilder(connection)
                                                .maxContentLength(Integer.MAX_VALUE)
                                                .propagateSettings(false)
                                                .build()
                                ))
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
                                                .addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                                                    @Override
                                                    protected void channelRead0(ChannelHandlerContext ctx_, FullHttpResponse msg) throws Exception {
                                                        responses.add(msg.retain())
                                                        if (--expectedResponses == 0) {
                                                            ctx_.close()
                                                        }
                                                    }

                                                    @Override
                                                    void exceptionCaught(ChannelHandlerContext ctx_, Throwable cause) throws Exception {
                                                        cause.printStackTrace()
                                                        responses.add(cause) // this will cause the conditions below to fail
                                                        ctx_.close()
                                                    }
                                                })


                                        def requestIndex = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path)
                                        requestIndex.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), 'https')
                                        ctx.channel().writeAndFlush(requestIndex)
                                    }
                                })
                    }
                })

        bootstrap.connect().await().channel().closeFuture().await()

        return responses
    }

    def 'with push enabled'() {
        given:
        def responses = request(true, '/serverPush/manual')

        expect:
        responses.size() == 3

        responses[0] instanceof FullHttpResponse
        responses[0].content().toString(StandardCharsets.UTF_8) == 'push supported: true'

        responses[1] instanceof FullHttpResponse
        responses[1].content().toString(StandardCharsets.UTF_8) == 'bar' ||
                responses[1].content().toString(StandardCharsets.UTF_8) == 'baz'

        responses[2] instanceof FullHttpResponse
        responses[2].content().toString(StandardCharsets.UTF_8) == 'bar' ||
                responses[2].content().toString(StandardCharsets.UTF_8) == 'baz'
        responses[1].content().toString(StandardCharsets.UTF_8) != responses[2].content().toString(StandardCharsets.UTF_8)
    }

    def 'with push enabled: automatic'() {
        given:
        def responses = request(true, '/serverPush/automatic')

        expect:
        responses.size() == 3

        responses[0] instanceof FullHttpResponse
        responses[0].content().toString(StandardCharsets.UTF_8) == 'push supported: true'

        responses[1] instanceof FullHttpResponse
        responses[1].content().toString(StandardCharsets.UTF_8) == 'bar' ||
                responses[1].content().toString(StandardCharsets.UTF_8) == 'baz'

        responses[2] instanceof FullHttpResponse
        responses[2].content().toString(StandardCharsets.UTF_8) == 'bar' ||
                responses[2].content().toString(StandardCharsets.UTF_8) == 'baz'
        responses[1].content().toString(StandardCharsets.UTF_8) != responses[2].content().toString(StandardCharsets.UTF_8)
    }

    def 'with push disabled'() {
        given:
        def responses = request(false, '/serverPush/manual')

        expect:
        responses.size() == 1

        responses[0] instanceof FullHttpResponse
        responses[0].content().toString(StandardCharsets.UTF_8) == 'push supported: false'
    }


    @Requires(property = "spec.name", value = "Http2ServerPushSpec")
    @Controller("/serverPush")
    static class SameSiteController {
        @Get("/manual")
        HttpResponse<String> manual(HttpRequest<?> request) {
            return HttpResponse.ok('push supported: ' + request.isServerPushSupported())
                    .serverPush(URI.create("/serverPush/resource1"), resource1())
                    .serverPush(URI.create("/serverPush/resource2"), resource2())
        }

        @Get("/automatic")
        HttpResponse<String> automatic(HttpRequest<?> request) {
            request.serverPush(HttpRequest.GET('/serverPush/resource1'))
            request.serverPush(HttpRequest.GET('/serverPush/resource2'))
            return HttpResponse.ok('push supported: ' + request.isServerPushSupported())
        }

        @Get("/resource1")
        HttpResponse<?> resource1() {
            return HttpResponse.ok(Unpooled.wrappedBuffer('bar'.getBytes(StandardCharsets.UTF_8)))
        }

        @Get("/resource2")
        HttpResponse<?> resource2() {
            return HttpResponse.ok(Unpooled.wrappedBuffer('baz'.getBytes(StandardCharsets.UTF_8)))
        }
    }
}
