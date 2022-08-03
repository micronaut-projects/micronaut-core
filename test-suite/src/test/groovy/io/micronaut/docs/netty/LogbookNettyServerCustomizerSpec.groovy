package io.micronaut.docs.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.http.server.netty.NettyHttpServer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpClientUpgradeHandler
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
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
import org.zalando.logbook.HttpRequest
import org.zalando.logbook.HttpResponse
import org.zalando.logbook.Logbook
import org.zalando.logbook.Strategy
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class LogbookNettyServerCustomizerSpec extends Specification {
    private EmbeddedChannel connectClientEmbeddedChannel(EmbeddedChannel serverEmbeddedChannel) {
        def clientEmbeddedChannel = new EmbeddedChannel()

        serverEmbeddedChannel.pipeline()
                .addFirst(new ChannelOutboundHandlerAdapter() {
                    @Override
                    void write(ChannelHandlerContext ctx_, Object msg, ChannelPromise promise) throws Exception {
                        // forward to client
                        clientEmbeddedChannel.eventLoop().execute(() -> clientEmbeddedChannel.writeOneInbound(msg))
                    }

                    @Override
                    void flush(ChannelHandlerContext ctx_) throws Exception {
                        clientEmbeddedChannel.eventLoop().execute(() -> clientEmbeddedChannel.flushInbound())
                    }
                })
        clientEmbeddedChannel.pipeline()
                .addLast(new ChannelOutboundHandlerAdapter() {
                    @Override
                    void write(ChannelHandlerContext ctx_, Object msg, ChannelPromise promise) throws Exception {
                        serverEmbeddedChannel.eventLoop().execute(() -> serverEmbeddedChannel.writeOneInbound(msg))
                    }

                    @Override
                    void flush(ChannelHandlerContext ctx_) throws Exception {
                        serverEmbeddedChannel.eventLoop().execute(() -> serverEmbeddedChannel.flushInbound())
                    }
                })

        serverEmbeddedChannel.pipeline().fireChannelActive()
        return clientEmbeddedChannel
    }

    private void runPending(EmbeddedChannel... channels) {
        // https://github.com/netty/netty/pull/12536 will make this more sensible :)
        for (int i = 0; i < 30; i++) {
            for (EmbeddedChannel channel : channels) {
                channel.runPendingTasks()
            }
        }
    }

    def 'plaintext http 1'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'LogbookNettyServerCustomizerSpec'])
        ctx.getBean(Logbook)

        def serverEmbeddedChannel = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        def clientEmbeddedChannel = connectClientEmbeddedChannel(serverEmbeddedChannel)
        clientEmbeddedChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(1024))

        def request1 = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                '/logbook/logged',
                Unpooled.wrappedBuffer('foo'.getBytes(StandardCharsets.UTF_8))
        )
        request1.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        request1.headers().add(HttpHeaderNames.CONTENT_LENGTH, 3)

        when:
        clientEmbeddedChannel.writeOneOutbound(request1)
        clientEmbeddedChannel.flushOutbound()

        runPending(clientEmbeddedChannel, serverEmbeddedChannel)

        then:
        FullHttpResponse response = clientEmbeddedChannel.readInbound()
        response.status() == HttpResponseStatus.OK
        response.content().toString(StandardCharsets.UTF_8) == 'foo'

        ctx.getBean(LogbookFactory).log == [
                'POST /logbook/logged',
                'foo',
                '200',
                'foo',
        ]
    }

    def 'tls alpn http 2'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.server.http-version'       : '2.0',
                'micronaut.server.ssl.enabled'        : true,
                'micronaut.server.ssl.buildSelfSigned': true,
                'spec.name'                           : 'LogbookNettyServerCustomizerSpec'
        ])

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

        def serverEmbeddedChannel = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(true)
        def clientEmbeddedChannel = connectClientEmbeddedChannel(serverEmbeddedChannel)

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
        clientEmbeddedChannel.pipeline()
                .addLast(sslContext.newHandler(clientEmbeddedChannel.alloc(), "localhost", 443))
                .addLast(new ApplicationProtocolNegotiationHandler('') {
                    @Override
                    protected void configurePipeline(ChannelHandlerContext ctx_, String protocol) throws Exception {
                        if (ApplicationProtocolNames.HTTP_2 != protocol) {
                            throw new AssertionError((Object) protocol)
                        }
                        ctx_.pipeline()
                                .addLast(connectionHandler)


                        def requestIndex = new DefaultFullHttpRequest(
                                HttpVersion.HTTP_1_1,
                                HttpMethod.POST,
                                '/logbook/logged',
                                Unpooled.wrappedBuffer('foo'.getBytes(StandardCharsets.UTF_8))
                        )
                        requestIndex.headers()
                                .add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), 'https')
                                .add(HttpHeaderNames.CONTENT_LENGTH, 3)
                        ctx_.channel().writeAndFlush(requestIndex)
                    }
                })

        when:
        runPending(clientEmbeddedChannel, serverEmbeddedChannel)

        then:
        FullHttpResponse response = clientEmbeddedChannel.readInbound()
        response.status() == HttpResponseStatus.OK
        response.content().toString(StandardCharsets.UTF_8) == 'foo'

        ctx.getBean(LogbookFactory).log == [
                'POST /logbook/logged',
                'foo',
                '200',
                'foo',
        ]
    }

    def 'tls alpn http 1'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.server.ssl.enabled'        : true,
                'micronaut.server.ssl.buildSelfSigned': true,
                'spec.name'                           : 'LogbookNettyServerCustomizerSpec'
        ])

        def sslContext = SslContextBuilder.forClient()
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_1_1))
                .build()

        def serverEmbeddedChannel = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(true)
        def clientEmbeddedChannel = connectClientEmbeddedChannel(serverEmbeddedChannel)

        clientEmbeddedChannel.pipeline()
                .addLast(sslContext.newHandler(clientEmbeddedChannel.alloc(), "localhost", 443))
                .addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                    @Override
                    protected void configurePipeline(ChannelHandlerContext ctx_, String protocol) throws Exception {
                        if (ApplicationProtocolNames.HTTP_1_1 != protocol) {
                            throw new AssertionError((Object) protocol)
                        }
                        ctx_.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024))

                        def requestIndex = new DefaultFullHttpRequest(
                                HttpVersion.HTTP_1_1,
                                HttpMethod.POST,
                                '/logbook/logged',
                                Unpooled.wrappedBuffer('foo'.getBytes(StandardCharsets.UTF_8))
                        )
                        requestIndex.headers()
                                .add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), 'https')
                                .add(HttpHeaderNames.CONTENT_LENGTH, 3)
                        ctx_.channel().writeAndFlush(requestIndex)
                    }
                })

        when:
        runPending(clientEmbeddedChannel, serverEmbeddedChannel)

        then:
        FullHttpResponse response = clientEmbeddedChannel.readInbound()
        response.status() == HttpResponseStatus.OK
        response.content().toString(StandardCharsets.UTF_8) == 'foo'

        ctx.getBean(LogbookFactory).log == [
                'POST /logbook/logged',
                'foo',
                '200',
                'foo',
        ]
    }

    def 'h2c do not upgrade'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.server.http-version': '2.0',
                'spec.name'                    : 'LogbookNettyServerCustomizerSpec'
        ])


        def serverEmbeddedChannel = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        def clientEmbeddedChannel = connectClientEmbeddedChannel(serverEmbeddedChannel)

        clientEmbeddedChannel.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(1024))

        def request1 = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                '/logbook/logged'
        )
        request1.headers()
                .add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), 'https')

        def request2 = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                '/logbook/logged',
                Unpooled.wrappedBuffer('bar'.getBytes(StandardCharsets.UTF_8))
        )
        request2.headers()
                .add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), 'https')
                .add(HttpHeaderNames.CONTENT_LENGTH, 3)

        when:
        clientEmbeddedChannel.writeAndFlush(request1)
        runPending(clientEmbeddedChannel, serverEmbeddedChannel)

        then:
        FullHttpResponse response1 = clientEmbeddedChannel.readInbound()
        response1.status() == HttpResponseStatus.OK
        response1.content().toString(StandardCharsets.UTF_8) == 'hello'

        when:
        clientEmbeddedChannel.writeAndFlush(request2)
        runPending(clientEmbeddedChannel, serverEmbeddedChannel)

        then:
        FullHttpResponse response2 = clientEmbeddedChannel.readInbound()
        response2.status() == HttpResponseStatus.OK
        response2.content().toString(StandardCharsets.UTF_8) == 'bar'

        ctx.getBean(LogbookFactory).log == [
                'GET /logbook/logged',
                '',
                '200',
                'hello',
                'POST /logbook/logged',
                'bar',
                '200',
                // second response body not included because of logbook bug: https://github.com/zalando/logbook/issues/1216
                //'bar',
        ]
    }

    def 'h2c with upgrade'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.server.http-version': '2.0',
                'spec.name'                    : 'LogbookNettyServerCustomizerSpec'
        ])


        def serverEmbeddedChannel = ((NettyHttpServer) ctx.getBean(EmbeddedServer)).buildEmbeddedChannel(false)
        def clientEmbeddedChannel = connectClientEmbeddedChannel(serverEmbeddedChannel)

        def http2Connection = new DefaultHttp2Connection(false)
        def inboundAdapter = new InboundHttp2ToHttpAdapterBuilder(http2Connection)
                .maxContentLength(1000000)
                .validateHttpHeaders(true)
                .propagateSettings(true)
                .build()
        def connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
                .connection(http2Connection)
                .frameListener(new DelegatingDecompressorFrameListener(http2Connection, inboundAdapter))
                .build()


        def clientCodec = new HttpClientCodec()
        clientEmbeddedChannel.pipeline()
                .addLast(clientCodec)
                .addLast(new HttpClientUpgradeHandler(clientCodec, new Http2ClientUpgradeCodec(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, connectionHandler), 1024))

        def request1 = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                '/logbook/logged'
        )
        request1.headers()
                .add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), 'https')

        def request2 = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                '/logbook/logged',
                Unpooled.wrappedBuffer('bar'.getBytes(StandardCharsets.UTF_8))
        )
        request2.headers()
                .add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), 'https')
                .add(HttpHeaderNames.CONTENT_LENGTH, 3)

        when:
        clientEmbeddedChannel.writeAndFlush(request1)
        runPending(clientEmbeddedChannel, serverEmbeddedChannel)

        then:
        (Http2Settings) clientEmbeddedChannel.readInbound()

        FullHttpResponse response1 = clientEmbeddedChannel.readInbound()
        response1.status() == HttpResponseStatus.OK
        response1.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text()) == 1
        response1.content().toString(StandardCharsets.UTF_8) == 'hello'

        when:
        clientEmbeddedChannel.writeAndFlush(request2)
        runPending(clientEmbeddedChannel, serverEmbeddedChannel)

        then:
        FullHttpResponse response2 = clientEmbeddedChannel.readInbound()
        response2.status() == HttpResponseStatus.OK
        response2.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text()) == 3
        response2.content().toString(StandardCharsets.UTF_8) == 'bar'

        ctx.getBean(LogbookFactory).log == [
                'GET /logbook/logged',
                '',
                '200',
                'hello',
                'POST /logbook/logged',
                'bar',
                '200',
                // second response body not included because of logbook bug: https://github.com/zalando/logbook/issues/1216
                //'bar',
        ]
    }

    @Requires(property = 'spec.name', value = 'LogbookNettyServerCustomizerSpec')
    @Factory
    static class LogbookFactory {
        List<String> log = new ArrayList<>()

        @Bean
        Logbook logbook() {
            return new Logbook() {
                @Override
                Logbook.RequestWritingStage process(HttpRequest request) throws IOException {
                    log.add(request.getMethod() + ' ' + request.getPath())
                    request = request.withBody()
                    return new Logbook.RequestWritingStage() {
                        @Override
                        Logbook.ResponseProcessingStage write() throws IOException {
                            return this
                        }

                        @Override
                        Logbook.ResponseWritingStage process(HttpResponse response) throws IOException {
                            log.add(request.getBodyAsString())
                            log.add(response.getStatus().toString())

                            response = response.withBody()
                            return new Logbook.ResponseWritingStage() {
                                @Override
                                void write() throws IOException {
                                    log.add(response.getBodyAsString())
                                }
                            }
                        }
                    }
                }

                @Override
                Logbook.RequestWritingStage process(HttpRequest request, Strategy strategy) throws IOException {
                    return process(request)
                }
            }
        }
    }

    @Controller("/logbook/logged")
    static class LoggedController {
        @Get("/")
        @Produces(MediaType.TEXT_PLAIN)
        String index() {
            return "hello"
        }

        @Post("/")
        @Produces(MediaType.TEXT_PLAIN)
        String index(@Body String body) {
            return body
        }
    }
}
