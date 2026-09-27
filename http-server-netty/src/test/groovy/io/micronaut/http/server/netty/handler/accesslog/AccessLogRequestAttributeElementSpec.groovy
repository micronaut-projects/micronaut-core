package io.micronaut.http.server.netty.handler.accesslog

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpClientUpgradeHandler
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec
import io.netty.handler.codec.http2.Http2Settings
import io.netty.handler.codec.http2.HttpConversionUtil
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder
import jakarta.inject.Singleton
import org.jspecify.annotations.NonNull
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The {@code %{name}r} element logs a request attribute of the request that the response belongs
 * to, also when the next request is already being processed.
 */
class AccessLogRequestAttributeElementSpec extends Specification {

    private static final String LOGGER = 'AccessLogRequestAttributeElementSpec'

    def 'http1.1 logs the request attribute of each pipelined request'() {
        given:
        def ctx = start('%m %U %{routeId}r', [:])
        def listAppender = appender()
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        def group = new NioEventLoopGroup(1)
        def channel = http1Client(group, ctx.getBean(EmbeddedServer), responses)

        when: 'the first response is only written after the second request has been routed'
        channel.write(request('/route-attribute/open/first'))
        channel.write(request('/route-attribute/finish/second'))
        channel.writeAndFlush(request('/route-attribute/none'))

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 3
            listAppender.list.size() == 3
        }
        responses*.content()*.toString(StandardCharsets.UTF_8) == ['open', 'finish', 'none']
        listAppender.list*.formattedMessage == [
                'GET /route-attribute/open/first first',
                'GET /route-attribute/finish/second second',
                'GET /route-attribute/none -',
        ]

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        channel?.close()
        group.shutdownGracefully()
        ctx.close()
    }

    def 'values are escaped and missing attributes log a dash'() {
        given:
        def ctx = start('"%r" %s %{routeId}r %{missing}r', [:])
        def listAppender = appender()
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        def group = new NioEventLoopGroup(1)
        def channel = http1Client(group, ctx.getBean(EmbeddedServer), responses)

        when:
        channel.write(request('/route-attribute/quoted'))
        channel.writeAndFlush(request('/route-attribute/none'))

        then:
        new PollingConditions(timeout: 5).eventually {
            listAppender.list.size() == 2
        }
        listAppender.list*.formattedMessage == [
                '"GET /route-attribute/quoted HTTP/1.1" 200 a\\"b -',
                '"GET /route-attribute/none HTTP/1.1" 200 - -',
        ]

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        channel?.close()
        group.shutdownGracefully()
        ctx.close()
    }

    def 'h2c logs the request attribute of each concurrent stream'() {
        given:
        def ctx = start('%U %{routeId}r', [
                'micronaut.server.http-version': '2.0',
                'micronaut.ssl.enabled'        : false,
        ])
        def server = ctx.getBean(EmbeddedServer)
        def listAppender = appender()

        def upgrade = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/route-attribute/set/one')
        upgrade.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        def open = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/route-attribute/open/two')
        open.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        open.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3)
        def finish = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/route-attribute/finish/three')
        finish.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        finish.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5)

        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        def group = new NioEventLoopGroup(1)
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel ch) throws Exception {
                        def connection = new DefaultHttp2Connection(false)
                        def connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
                                .initialSettings(Http2Settings.defaultSettings())
                                .frameListener(new DelegatingDecompressorFrameListener(
                                        connection,
                                        new InboundHttp2ToHttpAdapterBuilder(connection)
                                                .maxContentLength(Integer.MAX_VALUE)
                                                .propagateSettings(false)
                                                .build()
                                ))
                                .connection(connection)
                                .build()
                        def clientCodec = new HttpClientCodec()
                        def upgradeCodec = new Http2ClientUpgradeCodec(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, connectionHandler)
                        ch.pipeline()
                                .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC, clientCodec)
                                .addLast(new HttpClientUpgradeHandler(clientCodec, upgradeCodec, 1000000))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NonNull ChannelHandlerContext ctx_, @NonNull Object msg) throws Exception {
                                        if (responses.isEmpty()) {
                                            ctx_.channel().write(open)
                                            ctx_.channel().writeAndFlush(finish)
                                        }
                                        responses.add(msg)
                                    }
                                })
                    }
                })
                .remoteAddress(server.host, server.port)

        when:
        def channel = bootstrap.connect().sync().channel()
        channel.writeAndFlush(upgrade)

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 3
            listAppender.list.size() == 3
        }
        // the log order of concurrent streams is not defined
        listAppender.list*.formattedMessage.toSet() == [
                '/route-attribute/set/one one',
                '/route-attribute/finish/three three',
                '/route-attribute/open/two two',
        ].toSet()

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        channel?.close()
        group.shutdownGracefully()
        ctx.close()
    }

    private static ApplicationContext start(String format, Map<String, Object> extra) {
        def ctx = ApplicationContext.run([
                'spec.name'                                    : 'AccessLogRequestAttributeElementSpec',
                'micronaut.server.netty.access-logger.enabled' : true,
                'micronaut.server.netty.access-logger.logger-name': LOGGER,
                'micronaut.server.netty.access-logger.log-format' : format,
        ] + extra)
        ctx.getBean(EmbeddedServer).start()
        return ctx
    }

    private ListAppender<ILoggingEvent> appender() {
        def listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        def logger = (Logger) LoggerFactory.getLogger(LOGGER)
        logger.addAppender(listAppender)
        return listAppender
    }

    private static Channel http1Client(NioEventLoopGroup group, EmbeddedServer server, List<FullHttpResponse> responses) {
        return new Bootstrap()
                .group(group)
                .channel(NioSocketChannel)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NonNull ChannelHandlerContext ctx_, @NonNull Object msg) throws Exception {
                                        responses.add((FullHttpResponse) msg)
                                    }
                                })
                    }
                })
                .remoteAddress(server.host, server.port)
                .connect().sync().channel()
    }

    private static DefaultFullHttpRequest request(String uri) {
        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
        request.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        return request
    }

    @Requires(property = 'spec.name', value = 'AccessLogRequestAttributeElementSpec')
    @Controller('/route-attribute')
    @Singleton
    static class RouteAttributeController {
        CompletableFuture<HttpResponse<?>> lastFuture

        // completes when /finish is requested, after the next request has been routed
        @Get('/open/{id}')
        Mono<HttpResponse<?>> open(HttpRequest<?> request, String id) {
            request.setAttribute('routeId', id)
            return Mono.fromFuture(lastFuture = new CompletableFuture<>())
        }

        @Get('/finish/{id}')
        HttpResponse<?> finish(HttpRequest<?> request, String id) {
            request.setAttribute('routeId', id)
            lastFuture.complete(HttpResponse.ok('open'))
            return HttpResponse.ok('finish')
        }

        @Get('/set/{id}')
        HttpResponse<?> set(HttpRequest<?> request, String id) {
            request.setAttribute('routeId', id)
            return HttpResponse.ok(id)
        }

        @Get('/quoted')
        HttpResponse<?> quoted(HttpRequest<?> request) {
            request.setAttribute('routeId', 'a"b')
            return HttpResponse.ok('quoted')
        }

        @Get('/none')
        HttpResponse<?> none() {
            return HttpResponse.ok('none')
        }
    }
}
