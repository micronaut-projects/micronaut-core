package io.micronaut.http.server.netty.handler.accesslog

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
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
import io.netty.handler.codec.http.HttpMessage
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec
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
import io.netty.util.ReferenceCountUtil
import jakarta.inject.Singleton
import org.jetbrains.annotations.NotNull
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

class AccessLogSpec extends Specification {
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6782')
    def 'http1.1 concurrent pipelined requests'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'AccessLogSpec',
                'micronaut.server.netty.access-logger.enabled': true,
                'micronaut.server.netty.access-logger.logger-name': 'http-access-log',
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()

        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup(1))
                .channel(NioSocketChannel)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NotNull Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NotNull ChannelHandlerContext ctx_, @NotNull Object msg) throws Exception {
                                        responses.add(msg)
                                    }
                                })
                    }
                })
                .remoteAddress(server.host, server.port)

        def request1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/open')
        request1.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        def request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/finish')
        request2.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)

        def listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        ((Logger) LoggerFactory.getLogger('http-access-log')).addAppender(listAppender)

        when:
        def channel = bootstrap.connect().sync().channel()
        channel.write(request1)
        channel.writeAndFlush(request2)

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 2
        }
        responses[0].content().toString(StandardCharsets.UTF_8) == 'open'
        responses[1].content().toString(StandardCharsets.UTF_8) == 'finish'

        new PollingConditions(timeout: 5).eventually {
            listAppender.list.size() == 2
        }
        listAppender.list[0].message.contains('/interleave/open')
        listAppender.list[1].message.contains('/interleave/finish')

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        server.close()
        channel.close()
        bootstrap.config().group().shutdownGracefully()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6782')
    def 'http1.1 concurrent pipelined requests with exclusions'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'AccessLogSpec',
                'micronaut.server.netty.access-logger.enabled': true,
                'micronaut.server.netty.access-logger.logger-name': 'http-access-log',
                'micronaut.server.netty.access-logger.exclusions[0]': '/interleave/open',
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()

        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup(1))
                .channel(NioSocketChannel)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NotNull Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NotNull ChannelHandlerContext ctx_, @NotNull Object msg) throws Exception {
                                        responses.add(msg)
                                    }
                                })
                    }
                })
                .remoteAddress(server.host, server.port)

        def request1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/open')
        request1.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        def request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/finish')
        request2.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)

        def listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        ((Logger) LoggerFactory.getLogger('http-access-log')).addAppender(listAppender)

        when:
        def channel = bootstrap.connect().sync().channel()
        channel.write(request1)
        channel.writeAndFlush(request2)

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 2
        }
        responses[0].content().toString(StandardCharsets.UTF_8) == 'open'
        responses[1].content().toString(StandardCharsets.UTF_8) == 'finish'

        new PollingConditions(timeout: 5).eventually {
            listAppender.list.size() == 1
        }
        listAppender.list[0].message.contains('/interleave/finish')

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        server.close()
        channel.close()
        bootstrap.config().group().shutdownGracefully()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6782')
    def 'http1.1 continue status'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'AccessLogSpec',
                'micronaut.server.netty.access-logger.enabled': true,
                'micronaut.server.netty.access-logger.logger-name': 'http-access-log',
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()

        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup(1))
                .channel(NioSocketChannel)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NotNull Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NotNull ChannelHandlerContext ctx_, @NotNull Object msg) throws Exception {
                                        responses.add(msg)
                                    }
                                })
                    }
                })
                .remoteAddress(server.host, server.port)

        def request1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/interleave/post', Unpooled.wrappedBuffer('foo'.getBytes(StandardCharsets.UTF_8)))
        request1.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        request1.headers().add(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
        request1.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
        request1.headers().add(HttpHeaderNames.CONTENT_LENGTH, 3)
        def request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/simple')
        request2.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)

        def listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        ((Logger) LoggerFactory.getLogger('http-access-log')).addAppender(listAppender)

        when:
        def channel = bootstrap.connect().sync().channel()
        channel.write(request1)
        channel.writeAndFlush(request2)

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 3
        }
        responses[0].status() == HttpResponseStatus.CONTINUE
        responses[1].content().toString(StandardCharsets.UTF_8) == 'post: foo'
        responses[2].content().toString(StandardCharsets.UTF_8) == 'simple'

        new PollingConditions(timeout: 5).eventually {
            listAppender.list.size() == 2
        }
        listAppender.list[0].message.contains('/interleave/post')
        listAppender.list[1].message.contains('/interleave/simple')

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        server.close()
        channel.close()
        bootstrap.config().group().shutdownGracefully()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6782')
    def 'http2 truly concurrent requests'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'AccessLogSpec',
                'micronaut.server.http-version': '2.0',
                'micronaut.ssl.enabled': true,
                'micronaut.ssl.port': -1,
                'micronaut.ssl.buildSelfSigned': true,
                'micronaut.server.netty.access-logger.enabled': true,
                'micronaut.server.netty.access-logger.logger-name': 'http-access-log',
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()

        def request1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/open')
        request1.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 1)
        request1.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        def request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/simple')
        request2.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3)
        request2.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        def request3 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/finish')
        request3.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5)
        request3.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')

        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
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
        Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup(1))
                .channel(NioSocketChannel)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NotNull Channel ch) throws Exception {
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
                        ch.pipeline()
                                .addLast(sslContext.newHandler(ch.alloc(), server.host, server.port))
                                .addLast(new ApplicationProtocolNegotiationHandler('') {
                                    @Override
                                    protected void configurePipeline(ChannelHandlerContext ctx_, String protocol) throws Exception {
                                        if (ApplicationProtocolNames.HTTP_2 != protocol) {
                                            throw new AssertionError((Object) protocol)
                                        }
                                        ctx_.pipeline()
                                                .addLast(connectionHandler)
                                                .addLast(new ChannelInboundHandlerAdapter() {
                                                    @Override
                                                    void channelRead(@NotNull ChannelHandlerContext ctx__, @NotNull Object msg) throws Exception {
                                                        responses.add(msg)
                                                    }
                                                })


                                        ctx_.channel().write(request1)
                                        ctx_.channel().write(request2)
                                        ctx_.channel().writeAndFlush(request3)
                                    }
                                })
                    }
                })
                .remoteAddress(server.host, server.port)

        def listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        ((Logger) LoggerFactory.getLogger('http-access-log')).addAppender(listAppender)

        when:
        def channel = bootstrap.connect().sync().channel()

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 3
        }
        responses[0].content().toString(StandardCharsets.UTF_8) == 'simple'
        responses[1].content().toString(StandardCharsets.UTF_8) == 'open'
        responses[2].content().toString(StandardCharsets.UTF_8) == 'finish'

        new PollingConditions(timeout: 5).eventually {
            listAppender.list.size() == 3
        }
        listAppender.list[0].message.contains('/interleave/simple')
        listAppender.list[1].message.contains('/interleave/open')
        listAppender.list[2].message.contains('/interleave/finish')

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        server.close()
        channel.close()
        bootstrap.config().group().shutdownGracefully()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6782')
    def 'h2c truly concurrent requests with http1 upgrade'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'AccessLogSpec',
                'micronaut.server.http-version': '2.0',
                'micronaut.ssl.enabled': false,
                'micronaut.server.netty.access-logger.enabled': true,
                'micronaut.server.netty.access-logger.logger-name': 'http-access-log',
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()

        def request1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/simple')
        request1.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        def request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/open')
        request2.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        request2.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3)
        def request3 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/simple')
        request3.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5)
        request3.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        def request4 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/finish')
        request4.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 7)
        request4.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')

        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup(1))
                .channel(NioSocketChannel)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NotNull Channel ch) throws Exception {
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
                        def upgradeHandler = new HttpClientUpgradeHandler(clientCodec, upgradeCodec, 1000000)
                        ch.pipeline()
                                .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC, clientCodec)
                                .addLast(upgradeHandler)
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NotNull ChannelHandlerContext ctx_, @NotNull Object msg) throws Exception {
                                        if (responses.isEmpty()) {
                                            ctx_.channel().write(request2)
                                            ctx_.channel().write(request3)
                                            ctx_.channel().writeAndFlush(request4)
                                        }
                                        responses.add(msg)
                                    }
                                })
                    }
                })
                .remoteAddress(server.host, server.port)

        def listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        ((Logger) LoggerFactory.getLogger('http-access-log')).addAppender(listAppender)

        when:
        def channel = bootstrap.connect().sync().channel()
        channel.writeAndFlush(request1)

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 4
        }
        responses[0].content().toString(StandardCharsets.UTF_8) == 'simple'
        responses[1].content().toString(StandardCharsets.UTF_8) == 'simple'
        responses[2].content().toString(StandardCharsets.UTF_8) == 'open'
        responses[3].content().toString(StandardCharsets.UTF_8) == 'finish'

        new PollingConditions(timeout: 5).eventually {
            listAppender.list.size() == 4
        }
        listAppender.list[0].message.contains('/interleave/simple')
        listAppender.list[1].message.contains('/interleave/simple')
        listAppender.list[2].message.contains('/interleave/open')
        listAppender.list[3].message.contains('/interleave/finish')

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        server.close()
        channel.close()
        bootstrap.config().group().shutdownGracefully()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6782')
    def 'h2c truly concurrent requests with http1 upgrade with exclusions'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'AccessLogSpec',
                'micronaut.server.http-version': '2.0',
                'micronaut.ssl.enabled': false,
                'micronaut.server.netty.access-logger.enabled': true,
                'micronaut.server.netty.access-logger.logger-name': 'http-access-log',
                'micronaut.server.netty.access-logger.exclusions[0]': '/interleave/.i.*', // exclude simple and finish
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()

        def request1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/simple')
        request1.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        def request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/open')
        request2.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        request2.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 3)
        def request3 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/simple')
        request3.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 5)
        request3.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')
        def request4 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/interleave/finish')
        request4.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), 7)
        request4.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), ':https')

        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        Bootstrap bootstrap = new Bootstrap()
                .group(new NioEventLoopGroup(1))
                .channel(NioSocketChannel)
                .option(ChannelOption.AUTO_READ, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NotNull Channel ch) throws Exception {
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
                        def upgradeHandler = new HttpClientUpgradeHandler(clientCodec, upgradeCodec, 1000000)
                        ch.pipeline()
                                .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC, clientCodec)
                                .addLast(upgradeHandler)
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NotNull ChannelHandlerContext ctx_, @NotNull Object msg) throws Exception {
                                        if (responses.isEmpty()) {
                                            ctx_.channel().write(request2)
                                            ctx_.channel().write(request3)
                                            ctx_.channel().writeAndFlush(request4)
                                        }
                                        responses.add(msg)
                                    }
                                })
                    }
                })
                .remoteAddress(server.host, server.port)

        def listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        ((Logger) LoggerFactory.getLogger('http-access-log')).addAppender(listAppender)

        when:
        def channel = bootstrap.connect().sync().channel()
        channel.writeAndFlush(request1)

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 4
        }
        responses[0].content().toString(StandardCharsets.UTF_8) == 'simple'
        responses[1].content().toString(StandardCharsets.UTF_8) == 'simple'
        responses[2].content().toString(StandardCharsets.UTF_8) == 'open'
        responses[3].content().toString(StandardCharsets.UTF_8) == 'finish'

        new PollingConditions(timeout: 5).eventually {
            listAppender.list.size() == 1
        }
        listAppender.list[0].message.contains('/interleave/open')

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        server.close()
        channel.close()
        bootstrap.config().group().shutdownGracefully()
    }

    @Requires(property = 'spec.name', value = 'AccessLogSpec')
    @Controller('/interleave')
    @Singleton
    static class InterleavingController {
        CompletableFuture<HttpResponse<?>> lastFuture

        // this endpoint completes when `/finish` is requested
        @Get('/open')
        def open() {
            return Mono.fromFuture(lastFuture = new CompletableFuture<>())
        }

        // this endpoint completes a previous request made to `/open`, and then returns normally
        @Get('/finish')
        def finish() {
            lastFuture.complete(HttpResponse.ok('open'))
            return HttpResponse.ok('finish')
        }

        // this endpoint does nothing special
        @Get('/simple')
        def simple() {
            return HttpResponse.ok('simple')
        }

        // this endpoint does nothing special
        @Post(value = '/post', consumes = 'text/plain')
        def post(@Body String body) {
            return HttpResponse.ok('post: ' + body)
        }
    }
}
