package io.micronaut.http.server.netty.handler.accesslog

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
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

    def 'control characters in values are escaped'() {
        given:
        def ctx = start('%U %{routeId}r', [:])
        def listAppender = appender()
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        def group = new NioEventLoopGroup(1)
        def channel = http1Client(group, ctx.getBean(EmbeddedServer), responses)

        when:
        channel.writeAndFlush(request('/route-attribute/control'))

        then:
        new PollingConditions(timeout: 5).eventually {
            listAppender.list.size() == 1
        }
        listAppender.list[0].formattedMessage == '/route-attribute/control a\\u001bb\\u000cc\\u0085d\\u2028e\\nf'

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        channel?.close()
        group.shutdownGracefully()
        ctx.close()
    }

    def 'optional values are unwrapped and a failing toString logs a dash'() {
        given:
        def ctx = start('%U %s %{routeId}r', [:])
        def listAppender = appender()
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        def group = new NioEventLoopGroup(1)
        def channel = http1Client(group, ctx.getBean(EmbeddedServer), responses)

        when:
        channel.write(request('/route-attribute/optional'))
        channel.write(request('/route-attribute/empty-optional'))
        channel.writeAndFlush(request('/route-attribute/throwing'))

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 3
            listAppender.list.size() == 3
        }
        responses*.status()*.code() == [200, 200, 200]
        listAppender.list*.formattedMessage == [
                '/route-attribute/optional 200 opt',
                '/route-attribute/empty-optional 200 -',
                '/route-attribute/throwing 200 -',
        ]

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        channel?.close()
        group.shutdownGracefully()
        ctx.close()
    }

    def 'an empty argument still logs the request line'() {
        given:
        def ctx = start('%{}r|%{routeId}r', [:])
        def listAppender = appender()
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        def group = new NioEventLoopGroup(1)
        def channel = http1Client(group, ctx.getBean(EmbeddedServer), responses)

        when:
        channel.writeAndFlush(request('/route-attribute/set/one'))

        then:
        new PollingConditions(timeout: 5).eventually {
            listAppender.list.size() == 1
        }
        listAppender.list[0].formattedMessage == 'GET /route-attribute/set/one HTTP/1.1|one'

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        channel?.close()
        group.shutdownGracefully()
        ctx.close()
    }

    def 'the websocket handshake response logs the request attribute'() {
        given:
        def ctx = start('%s %U %{routeId}r', [:])
        def listAppender = appender()
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        def group = new NioEventLoopGroup(1)
        def channel = http1Client(group, ctx.getBean(EmbeddedServer), responses)
        def upgrade = request('/route-attribute/ws')
        upgrade.headers()
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)
                .set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
                .set(HttpHeaderNames.SEC_WEBSOCKET_KEY, 'dGhlIHNhbXBsZSBub25jZQ==')
                .set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, '13')

        when:
        channel.writeAndFlush(upgrade)

        then:
        new PollingConditions(timeout: 5).eventually {
            listAppender.list.size() == 1
        }
        listAppender.list[0].formattedMessage == '101 /route-attribute/ws ws'

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        channel?.close()
        group.shutdownGracefully()
        ctx.close()
    }

    def 'an access log handler added by a customizer logs request attributes'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name'                    : 'AccessLogRequestAttributeElementSpec',
                'route-attribute.custom-logger': true,
        ])
        ctx.getBean(EmbeddedServer).start()
        def listAppender = appender()
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        def group = new NioEventLoopGroup(1)
        def channel = http1Client(group, ctx.getBean(EmbeddedServer), responses)

        when:
        channel.write(request('/route-attribute/set/one'))
        channel.writeAndFlush(request('/route-attribute/none'))

        then:
        new PollingConditions(timeout: 5).eventually {
            listAppender.list.size() == 2
        }
        listAppender.list*.formattedMessage == [
                '/route-attribute/set/one one',
                '/route-attribute/none -',
        ]

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        channel?.close()
        group.shutdownGracefully()
        ctx.close()
    }

    def 'http1.1 logs the attribute of the request that a filter replaced the original with'() {
        given:
        def ctx = start('%U %{routeId}r', [:])
        def listAppender = appender()
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        def group = new NioEventLoopGroup(1)
        def channel = http1Client(group, ctx.getBean(EmbeddedServer), responses)

        when:
        channel.write(request('/route-attribute/replaced/one'))
        channel.writeAndFlush(request('/route-attribute/replaced-missing'))

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 2
            listAppender.list.size() == 2
        }
        responses*.content()*.toString(StandardCharsets.UTF_8) == ['one', 'replaced-missing']
        listAppender.list*.formattedMessage == [
                '/route-attribute/replaced/one one',
                '/route-attribute/replaced-missing -',
        ]

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        channel?.close()
        group.shutdownGracefully()
        ctx.close()
    }

    def 'h2c logs the attribute of the request that a filter replaced the original with'() {
        given:
        def ctx = start('%U %{routeId}r', [
                'micronaut.server.http-version': '2.0',
                'micronaut.ssl.enabled'        : false,
        ])
        def listAppender = appender()
        def upgrade = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/route-attribute/replaced/one')
        upgrade.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        def stream = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/route-attribute/replaced/two')
        stream.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        stream.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3)
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        def group = new NioEventLoopGroup(1)

        when:
        def channel = h2cClient(group, ctx.getBean(EmbeddedServer), responses, [stream])
        channel.writeAndFlush(upgrade)

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 2
            listAppender.list.size() == 2
        }
        responses*.content()*.toString(StandardCharsets.UTF_8).toSet() == ['one', 'two'].toSet()
        listAppender.list*.formattedMessage.toSet() == [
                '/route-attribute/replaced/one one',
                '/route-attribute/replaced/two two',
        ].toSet()

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

    /**
     * An h2c client that upgrades with the first request it writes, and sends the given requests
     * as new streams once the upgrade response arrives.
     */
    private static Channel h2cClient(NioEventLoopGroup group, EmbeddedServer server, List<FullHttpResponse> responses, List<DefaultFullHttpRequest> afterUpgrade) {
        return new Bootstrap()
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
                                            afterUpgrade.forEach(r -> ctx_.channel().write(r))
                                            ctx_.channel().flush()
                                        }
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

        @Get('/control')
        HttpResponse<?> control(HttpRequest<?> request) {
            request.setAttribute('routeId', 'a\u001bb\fc\u0085d\u2028e\nf')
            return HttpResponse.ok('control')
        }

        @Get('/optional')
        HttpResponse<?> optional(HttpRequest<?> request) {
            request.setAttribute('routeId', Optional.of('opt'))
            return HttpResponse.ok('optional')
        }

        @Get('/empty-optional')
        HttpResponse<?> emptyOptional(HttpRequest<?> request) {
            request.setAttribute('routeId', Optional.empty())
            return HttpResponse.ok('empty-optional')
        }

        @Get('/throwing')
        HttpResponse<?> throwing(HttpRequest<?> request) {
            request.setAttribute('routeId', new ThrowingToString())
            return HttpResponse.ok('throwing')
        }

        @Get('/replaced/{id}')
        HttpResponse<?> replaced(HttpRequest<?> request, String id) {
            request.setAttribute('routeId', id)
            // prove that the filter replaced the request
            return HttpResponse.ok(request instanceof NettyHttpRequest ? 'original' : id)
        }

        @Get('/replaced-missing')
        HttpResponse<?> replacedMissing(HttpRequest<?> request) {
            return HttpResponse.ok(request instanceof NettyHttpRequest ? 'original' : 'replaced-missing')
        }

        @Get('/none')
        HttpResponse<?> none() {
            return HttpResponse.ok('none')
        }
    }

    static class ThrowingToString {
        @Override
        String toString() {
            throw new IllegalStateException('toString')
        }
    }

    @Requires(property = 'spec.name', value = 'AccessLogRequestAttributeElementSpec')
    @ServerWebSocket('/route-attribute/ws')
    static class RouteAttributeWebSocket {
        @OnMessage
        String onMessage(String message) {
            return message
        }
    }

    @Requires(property = 'spec.name', value = 'AccessLogRequestAttributeElementSpec')
    @ServerFilter('/route-attribute/ws')
    static class RouteAttributeWebSocketFilter {
        @RequestFilter
        void filter(HttpRequest<?> request) {
            request.setAttribute('routeId', 'ws')
        }
    }

    /**
     * Replaces the request with a new one, so the route sets its attributes on the replacement.
     */
    @Requires(property = 'spec.name', value = 'AccessLogRequestAttributeElementSpec')
    @ServerFilter(['/route-attribute/replaced/**', '/route-attribute/replaced-missing'])
    static class ReplacingFilter {
        @RequestFilter
        HttpRequest<?> replace(HttpRequest<?> request) {
            return HttpRequest.GET(request.uri.toString())
        }
    }

    /**
     * Adds an access log handler under a name of its own, with the configured access logger
     * disabled.
     */
    @Singleton
    @Requires(property = 'route-attribute.custom-logger', value = 'true')
    static class AccessLogCustomizer implements BeanCreatedEventListener<NettyServerCustomizer.Registry>, NettyServerCustomizer {
        @Override
        NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
            event.bean.register(this)
            return event.bean
        }

        @Override
        NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            return new Adding(channel)
        }

        static class Adding implements NettyServerCustomizer {
            final Channel channel

            Adding(Channel channel) {
                this.channel = channel
            }

            @Override
            NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
                return new Adding(channel)
            }

            @Override
            void onStreamPipelineBuilt() {
                channel.pipeline().addBefore(ChannelPipelineCustomizer.HANDLER_MICRONAUT_INBOUND, 'custom-access-log',
                        new HttpAccessLogHandler(LOGGER, '%U %{routeId}r'))
            }
        }
    }
}
