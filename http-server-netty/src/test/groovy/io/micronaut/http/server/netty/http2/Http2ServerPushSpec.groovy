package io.micronaut.http.server.netty.http2

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.PushCapableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener
import io.netty.handler.codec.http2.Http2Exception
import io.netty.handler.codec.http2.Http2FrameListener
import io.netty.handler.codec.http2.Http2FrameListenerDecorator
import io.netty.handler.codec.http2.Http2Headers
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
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NotNull
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

@MicronautTest
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.netty.log-level", value = "TRACE")
@Property(name = "micronaut.http.client.log-level", value = "TRACE")
@Property(name = "micronaut.server.ssl.enabled", value = "true")
@Property(name = "micronaut.server.ssl.port", value = "-1")
@Property(name = "micronaut.server.ssl.buildSelfSigned", value = "true")
@Property(name = "spec.name", value = "Http2ServerPushSpec")
class Http2ServerPushSpec extends Specification {
    @Inject
    EmbeddedServer embeddedServer

    def 'with push enabled: automatic'() {
        given:
        def runner = new Runner()
        runner.run(true, '/serverPush/automatic')

        expect:
        runner.responses.size() == 3

        runner.responses[0].content().toString(StandardCharsets.UTF_8) == 'push supported: true'

        runner.pushPromiseHeaders.any { it.scheme() == 'https' && it.path() == '/serverPush/resource1' }
        runner.responses.any { it.content().toString(StandardCharsets.UTF_8) == 'bar' }

        runner.pushPromiseHeaders.any { it.scheme() == 'https' && it.path() == '/serverPush/resource2' }
        runner.responses.any { it.content().toString(StandardCharsets.UTF_8) == 'baz' }

        cleanup:
        runner.responses*.content().forEach(ByteBuf::release)
    }

    def 'check headers'() {
        given:
        def runner = new Runner()
        runner.run(true, '/serverPush/automatic', new DefaultHttpHeaders()
                .add("authorization", "myauthtoken") // copied, but overwritten for resource1
                .add("x-someotherheader", "someothervalue") // copied
                .add("proxy-authorization", "proxyauthtoken") // not copied
                .add("referer", "https://micronaut.io/")) // overwritten

        expect:
        runner.responses.size() == 3

        runner.pushPromiseHeaders.any {
            it.scheme() == 'https' &&
                    it.path() == '/serverPush/resource1' &&
                    it.get("authorization") == 'bla' &&
                    it.get("x-someotherheader") == 'someothervalue' &&
                    it.get("proxy-authorization") == null &&
                    it.get("referer") == 'abc'
        }

        runner.pushPromiseHeaders.any {
            it.scheme() == 'https' &&
                    it.path() == '/serverPush/resource2'
                    it.get("authorization") == 'myauthtoken' &&
                    it.get("x-someotherheader") == 'someothervalue' &&
                    it.get("proxy-authorization") == null &&
                    it.get("referer") == '/serverPush/automatic'
        }
    }

    def 'with push disabled'() {
        given:
        def runner = new Runner()
        runner.run(false, '/serverPush/automatic')

        expect:
        runner.responses.size() == 1
        runner.responses[0].content().toString(StandardCharsets.UTF_8) == 'push supported: false'

        cleanup:
        runner.responses*.content().forEach(ByteBuf::release)
    }

    private class Runner {
        def completion = new CompletableFuture()
        def responses = new ArrayList<FullHttpResponse>()
        def pushPromiseHeaders = new ArrayList<Http2Headers>()
        int expectedResponses

        def run(boolean pushEnabled, String path, HttpHeaders headers = new DefaultHttpHeaders()) {
            expectedResponses = pushEnabled ? 3 : 1
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
                                    .frameListener(new FrameListener(new DelegatingDecompressorFrameListener(
                                            connection,
                                            new InboundHttp2ToHttpAdapterBuilder(connection)
                                                    .maxContentLength(Integer.MAX_VALUE)
                                                    .propagateSettings(false)
                                                    .build()
                                    )))
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
                                                    .addLast(new InboundHandler())


                                            def requestIndex = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path)
                                            requestIndex.headers().setAll(headers)
                                            requestIndex.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), 'https')
                                            ctx.channel().writeAndFlush(requestIndex)
                                        }
                                    })
                        }
                    })

            def channel = bootstrap.connect().await().channel()

            completion.get()

            channel.closeFuture().await()
        }

        class FrameListener extends Http2FrameListenerDecorator {
            FrameListener(Http2FrameListener listener) {
                super(listener)
            }

            @Override
            void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
                pushPromiseHeaders.add(headers)
                super.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding)
            }
        }

        class InboundHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
                responses.add(msg.retain())
                if (--expectedResponses == 0 || msg.status().code() >= 400) {
                    ctx.close()
                    completion.complete(null)
                }
            }

            @Override
            void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                ctx.close()
                completion.completeExceptionally(cause)
            }
        }
    }

    @Requires(property = "spec.name", value = "Http2ServerPushSpec")
    @Controller("/serverPush")
    static class SameSiteController {
        @Inject EmbeddedServer embeddedServer

        @Get("/automatic")
        HttpResponse<String> automatic(PushCapableHttpRequest<?> request) {
            if (request.isServerPushSupported()) {
                request.serverPush(HttpRequest.GET('/serverPush/resource1').header("Referer", "abc").header("Authorization", "bla"))
                request.serverPush(HttpRequest.GET('/serverPush/resource2'))
            }
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

        /**
         * This can be used for testing in a browser, it's not used by this spec directly
         */
        @Get(value = '/html', produces = 'text/html')
        String html(PushCapableHttpRequest<?> request) {
            if (request.isServerPushSupported()) {
                request.serverPush(HttpRequest.GET("/serverPush/resource1"))
                request.serverPush(HttpRequest.GET("/serverPush/resource2"))
            }
            @Language('HTML')
            def s = """<!doctype html>
<html lang="en">
<body>
push supported: ${request.isServerPushSupported()}
<iframe src="resource1"></iframe>
<iframe src="resource2"></iframe>
</body>
</html>"""
            return s
        }
    }
}
