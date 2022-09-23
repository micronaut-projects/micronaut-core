package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpVersion
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.http.server.netty.ssl.CertificateProvidedSslBuilder
import io.micronaut.http.ssl.SslConfiguration
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelId
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ServerChannel
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.HttpContentCompressor
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpServerUpgradeHandler
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2FrameCodec
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import io.netty.handler.codec.http2.Http2FrameStreamEvent
import io.netty.handler.codec.http2.Http2Headers
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2ResetFrame
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec
import io.netty.handler.codec.http2.Http2SettingsAckFrame
import io.netty.handler.codec.http2.Http2SettingsFrame
import io.netty.handler.codec.http2.Http2Stream
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.util.AsciiString
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

class ConnectionManagerSpec extends Specification {
    private static void patch(DefaultHttpClient httpClient, EmbeddedChannel... channels) {
        httpClient.connectionManager = new ConnectionManager(httpClient.connectionManager) {
            int i = 0

            @Override
            protected ChannelFuture doConnect(DefaultHttpClient.RequestKey requestKey, ChannelInitializer<? extends Channel> channelInitializer) {
                def channel = channels[i++]
                channel.pipeline().addLast(channelInitializer)
                def promise = channel.newPromise()
                promise.setSuccess()
                return promise
            }
        }
    }

    def 'simple http2 get'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn.clientChannel)

        def future = conn.testExchangeRequest(client)
        conn.exchangeSettings()
        conn.testExchangeResponse(future)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 streaming get'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn.clientChannel)

        def r1 = conn.testStreamingRequest(client)
        conn.exchangeSettings()
        conn.testStreamingResponse(r1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'simple http1 get'() {
        def ctx = ApplicationContext.run()
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn.clientChannel)

        conn.testExchange(client)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 get with compression'() {
        def ctx = ApplicationContext.run()
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        conn.serverChannel.pipeline().addLast(new HttpContentCompressor())
        patch(client, conn.clientChannel)

        def future = Mono.from(client.exchange(
                HttpRequest.GET('http://example.com/foo').header('accept-encoding', 'gzip'), String)).toFuture()
        future.exceptionally(t -> t.printStackTrace())
        conn.advance()

        assert conn.serverChannel.readInbound() instanceof io.netty.handler.codec.http.HttpRequest

        def response = new DefaultFullHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer("foo".bytes))
        response.headers().add('content-length', 3)
        conn.serverChannel.writeOutbound(response)

        conn.advance()
        assert future.get().status() == HttpStatus.OK
        assert future.get().body() == 'foo'

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 get with compression'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn.clientChannel)

        def future = Mono.from(client.exchange('https://example.com/foo', String)).toFuture()
        future.exceptionally(t -> t.printStackTrace())
        conn.exchangeSettings()

        Http2HeadersFrame request = conn.serverChannel.readInbound()
        def responseHeaders = new DefaultHttp2Headers()
        responseHeaders.add(Http2Headers.PseudoHeaderName.STATUS.value(), "200")
        responseHeaders.add('content-encoding', "gzip")
        conn.serverChannel.writeOutbound(new DefaultHttp2HeadersFrame(responseHeaders, false).stream(request.stream()))
        def compressedOut = new ByteArrayOutputStream()
        try (OutputStream os = new GZIPOutputStream(compressedOut)) {
            os.write('foo'.bytes)
        }
        conn.serverChannel.writeOutbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(compressedOut.toByteArray()), true).stream(request.stream()))

        conn.advance()
        def response = future.get()
        assert response.status() == HttpStatus.OK
        assert response.body() == 'foo'

        cleanup:
        client.close()
        ctx.close()
    }

    def 'simple http1 tls get'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1Tls()
        patch(client, conn.clientChannel)

        conn.testExchange(client)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'simple h2c get'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.plaintext-mode': 'h2c',
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupH2c()
        patch(client, conn.clientChannel)

        def future = conn.testExchangeRequest(client)
        conn.exchangeH2c()
        conn.testExchangeResponse(future)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 streaming get'() {
        def ctx = ApplicationContext.run()
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn.clientChannel)

        conn.testStreaming(client)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 concurrent stream'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp2()
        conn1.setupHttp2Tls()
        def conn2 = new EmbeddedTestConnectionHttp2()
        conn2.setupHttp2Tls()
        patch(client, conn1.clientChannel, conn2.clientChannel)

        when:
        // start two requests. this will open two connections
        def f1 = Mono.from(client.exchange('https://example.com/r1')).toFuture()
        f1.exceptionally(t -> t.printStackTrace())
        def f2 = Mono.from(client.exchange('https://example.com/r2')).toFuture()
        f2.exceptionally(t -> t.printStackTrace())

        then:
        // no data yet, haven't finished the handshake
        conn1.serverChannel.readInbound() == null

        when:
        // finish handshake for first connection
        conn1.exchangeSettings()
        then:
        // both requests immediately go to the first connection
        def req1 = conn1.serverChannel.<Http2HeadersFrame> readInbound()
        req1.headers().get(Http2Headers.PseudoHeaderName.PATH.value()) == '/r1'
        def req2 = conn1.serverChannel.<Http2HeadersFrame> readInbound()
        req2.stream().id() != req1.stream().id()
        req2.headers().get(Http2Headers.PseudoHeaderName.PATH.value()) == '/r2'

        when:
        // start a third request, this should reuse the existing connection
        def f3 = Mono.from(client.exchange('https://example.com/r3')).toFuture()
        f3.exceptionally(t -> t.printStackTrace())
        conn1.advance()
        then:
        def req3 = conn1.serverChannel.<Http2HeadersFrame> readInbound()
        req3.stream().id() != req1.stream().id()
        req3.stream().id() != req2.stream().id()
        req3.headers().get(Http2Headers.PseudoHeaderName.PATH.value()) == '/r3'

        // finish up the third request
        when:
        conn1.respondOk(req3.stream())
        conn1.advance()
        then:
        f3.get().status() == HttpStatus.OK

        // finish up the second and first request
        when:
        conn1.respondOk(req2.stream())
        conn1.respondOk(req1.stream())
        conn1.advance()
        then:
        f1.get().status() == HttpStatus.OK
        f2.get().status() == HttpStatus.OK

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 reuse'() {
        def ctx = ApplicationContext.run()
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn.clientChannel)

        conn.testExchange(client)
        conn.testStreaming(client)
        conn.testExchange(client)
        conn.testStreaming(client)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 plain text customization'() {
        given:
        def ctx = ApplicationContext.run()
        def client = ctx.getBean(DefaultHttpClient)
        def tracker = ctx.getBean(CustomizerTracker)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn.clientChannel)

        when:
        conn.testExchange(client)
        conn.testStreaming(client)

        then:
        def outerChannel = tracker.initialPipelineBuilt.poll()
        outerChannel.channel == conn.clientChannel
        outerChannel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC)
        outerChannel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_HTTP_DECODER)
        !outerChannel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_HTTP_AGGREGATOR)
        !outerChannel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_HTTP_STREAM)
        tracker.initialPipelineBuilt.isEmpty()

        def innerChannel = tracker.streamPipelineBuilt.poll()
        innerChannel.channel == conn.clientChannel
        innerChannel.handlerNames == outerChannel.handlerNames
        tracker.streamPipelineBuilt.isEmpty()

        def req1Channel = tracker.requestPipelineBuilt.poll()
        req1Channel.channel == conn.clientChannel
        req1Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_HTTP_AGGREGATOR)
        req1Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_MICRONAUT_FULL_HTTP_RESPONSE)

        def req2Channel = tracker.requestPipelineBuilt.poll()
        req2Channel.channel == conn.clientChannel
        req2Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE_FULL)
        req2Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE_STREAM)

        tracker.requestPipelineBuilt.isEmpty()

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 customization'(boolean secure) {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.client.plaintext-mode': 'h2c',
        ])
        def client = ctx.getBean(DefaultHttpClient)
        def tracker = ctx.getBean(CustomizerTracker)

        def conn = new EmbeddedTestConnectionHttp2()
        if (secure) {
            conn.setupHttp2Tls()
        } else {
            conn.setupH2c()
        }
        patch(client, conn.clientChannel)

        when:
        def r1 = conn.testExchangeRequest(client)
        if (secure) {
            conn.exchangeSettings()
        } else {
            conn.exchangeH2c()
        }
        conn.testExchangeResponse(r1)

        def r2 = conn.testStreamingRequest(client)
        conn.testStreamingResponse(r2)

        then:
        def outerChannel = tracker.initialPipelineBuilt.poll()
        outerChannel.channel == conn.clientChannel
        outerChannel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_SSL) == secure
        !outerChannel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION)
        tracker.initialPipelineBuilt.isEmpty()

        def innerChannel = tracker.streamPipelineBuilt.poll()
        innerChannel.channel == conn.clientChannel
        innerChannel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION)
        tracker.streamPipelineBuilt.isEmpty()

        def req1Channel = tracker.requestPipelineBuilt.poll()
        req1Channel.role == NettyClientCustomizer.ChannelRole.HTTP2_STREAM
        req1Channel.channel !== conn.clientChannel
        req1Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_HTTP_AGGREGATOR)
        req1Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_MICRONAUT_FULL_HTTP_RESPONSE)

        def req2Channel = tracker.requestPipelineBuilt.poll()
        req2Channel.role == NettyClientCustomizer.ChannelRole.HTTP2_STREAM
        req2Channel.channel !== conn.clientChannel
        req2Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE_FULL)
        req2Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE_STREAM)

        tracker.requestPipelineBuilt.isEmpty()

        cleanup:
        client.close()
        ctx.close()

        where:
        secure << [true, false]
    }

    def 'http1 exchange read timeout'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.read-timeout': '5s',
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn.clientChannel)

        // do one request
        conn.testExchange(client)
        conn.clientChannel.unfreezeTime()
        // connection is in reserve, should not time out
        TimeUnit.SECONDS.sleep(10)
        conn.advance()

        // second request
        def future = Mono.from(client.exchange('http://example.com/foo', String)).toFuture()
        conn.advance()

        // todo: move to advanceTime once IdleStateHandler supports it
        TimeUnit.SECONDS.sleep(5)
        conn.advance()

        assert future.isDone()
        when:
        future.get()
        then:
        def e = thrown ExecutionException
        e.cause instanceof ReadTimeoutException

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 exchange read timeout'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.client.read-timeout': '5s',
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn.clientChannel)

        // one request opens the connection
        def r1 = conn.testExchangeRequest(client)
        conn.exchangeSettings()
        conn.testExchangeResponse(r1)
        conn.clientChannel.unfreezeTime()

        // connection is in reserve, should not time out
        TimeUnit.SECONDS.sleep(10)
        conn.advance()

        // second request
        def future = Mono.from(client.exchange('https://example.com/foo', String)).toFuture()
        conn.advance()

        // todo: move to advanceTime once IdleStateHandler supports it
        TimeUnit.SECONDS.sleep(5)
        conn.advance()

        assert future.isDone()
        when:
        future.get()
        then:
        def e = thrown ExecutionException
        e.cause instanceof ReadTimeoutException

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 ttl'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.connect-ttl': '100s',
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp1()
        conn1.setupHttp1()
        def conn2 = new EmbeddedTestConnectionHttp1()
        conn2.setupHttp1()
        patch(client, conn1.clientChannel, conn2.clientChannel)

        def r1 = conn1.testExchangeRequest(client)
        conn1.clientChannel.advanceTimeBy(101, TimeUnit.SECONDS)
        conn1.testExchangeResponse(r1)

        // conn1 should expire now, conn2 will be the next connection
        conn2.testExchange(client)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 ttl'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.client.connect-ttl': '100s',
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp2()
        conn1.setupHttp2Tls()
        def conn2 = new EmbeddedTestConnectionHttp2()
        conn2.setupHttp2Tls()
        patch(client, conn1.clientChannel, conn2.clientChannel)

        def r1 = conn1.testExchangeRequest(client)
        conn1.exchangeSettings()
        conn1.clientChannel.advanceTimeBy(101, TimeUnit.SECONDS)
        conn1.testExchangeResponse(r1)

        // conn1 should expire now, conn2 will be the next connection
        def r2 = conn2.testExchangeRequest(client)
        conn2.exchangeSettings()
        conn2.testExchangeResponse(r2)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 pool timeout'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.connection-pool-idle-timeout': '5s',
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp1()
        conn1.setupHttp1()
        def conn2 = new EmbeddedTestConnectionHttp1()
        conn2.setupHttp1()
        patch(client, conn1.clientChannel, conn2.clientChannel)

        conn1.testExchange(client)
        conn1.clientChannel.unfreezeTime()
        // todo: move to advanceTime once IdleStateHandler supports it
        TimeUnit.SECONDS.sleep(5)
        conn1.advance()
        // conn1 should expire now, conn2 will be the next connection
        conn2.testExchange(client)

        cleanup:
        client.close()
        ctx.close()
    }

    static class EmbeddedTestConnectionBase {
        final EmbeddedChannel serverChannel
        final EmbeddedChannel clientChannel

        EmbeddedTestConnectionBase() {
            serverChannel = new EmbeddedServerChannel(new DummyChannelId('server'))
            serverChannel.freezeTime()
            serverChannel.config().setAutoRead(true)

            clientChannel = new EmbeddedChannel(new DummyChannelId('client'))
            clientChannel.freezeTime()
            EmbeddedTestUtil.connect(serverChannel, clientChannel)
        }

        void advance() {
            EmbeddedTestUtil.advance(serverChannel, clientChannel)
        }
    }

    static class EmbeddedServerChannel extends EmbeddedChannel implements ServerChannel {
        EmbeddedServerChannel(ChannelId channelId) {
            super(channelId)
        }
    }

    static class EmbeddedTestConnectionHttp1 extends EmbeddedTestConnectionBase {
        private String scheme

        void setupHttp1() {
            scheme = 'http'
            serverChannel.pipeline()
                    .addLast(new HttpServerCodec())
        }

        void setupHttp1Tls() {
            def certificate = new SelfSignedCertificate()
            def builder = SslContextBuilder.forServer(certificate.key(), certificate.cert())
            CertificateProvidedSslBuilder.setupSslBuilder(builder, new SslConfiguration(), HttpVersion.HTTP_1_1);
            def tlsHandler = builder.build().newHandler(ByteBufAllocator.DEFAULT)

            scheme = 'https'
            serverChannel.pipeline()
                    .addLast(tlsHandler)
                    .addLast(new HttpServerCodec())
        }

        void respondOk() {
            def response = new DefaultFullHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
            response.headers().add('content-length', 0)
            serverChannel.writeOutbound(response)
        }

        void testExchange(HttpClient client) {
            testExchangeResponse(testExchangeRequest(client))
        }

        CompletableFuture<HttpResponse<?>> testExchangeRequest(HttpClient client) {
            def future = Mono.from(client.exchange(scheme + '://example.com/foo')).toFuture()
            future.exceptionally(t -> t.printStackTrace())
            advance()
            return future
        }

        void testExchangeResponse(CompletableFuture<HttpResponse<?>> future) {
            io.netty.handler.codec.http.HttpRequest request = serverChannel.readInbound()
            assert request.uri() == '/foo'
            assert request.method() == HttpMethod.GET
            assert request.headers().get('host') == 'example.com'
            assert request.headers().get("connection") == "keep-alive"

            def tail = serverChannel.readInbound()
            assert tail == null || tail instanceof LastHttpContent

            respondOk()
            advance()

            assert future.get().status() == HttpStatus.OK
        }

        void testStreaming(StreamingHttpClient client) {
            def responseData = new ArrayDeque<String>()
            def responseComplete = false
            Flux.from(client.dataStream(HttpRequest.GET(scheme + '://example.com/foo')))
                    .doOnError(t -> t.printStackTrace())
                    .doOnComplete(() -> responseComplete = true)
                    .subscribe(b -> responseData.add(b.toString(StandardCharsets.UTF_8)))
            advance()

            io.netty.handler.codec.http.HttpRequest request = serverChannel.readInbound()
            assert request.uri() == '/foo'
            assert request.method() == HttpMethod.GET
            assert request.headers().get('host') == 'example.com'
            assert request.headers().get("connection") == "keep-alive"

            def tail = serverChannel.readInbound()
            assert tail == null || tail instanceof LastHttpContent

            def response = new DefaultHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
            response.headers().add('content-length', 6)
            serverChannel.writeOutbound(response)
            serverChannel.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer('foo'.bytes)))
            advance()

            assert responseData.poll() == 'foo'
            assert !responseComplete

            serverChannel.writeOutbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer('bar'.bytes)))
            advance()

            assert responseData.poll() == 'bar'
            assert responseComplete
        }
    }

    static class EmbeddedTestConnectionHttp2 extends EmbeddedTestConnectionBase {
        private String scheme
        Http2FrameStream h2cResponseStream

        void setupHttp2Tls() {
            scheme = 'https'

            def certificate = new SelfSignedCertificate()
            def builder = SslContextBuilder.forServer(certificate.key(), certificate.cert())
            CertificateProvidedSslBuilder.setupSslBuilder(builder, new SslConfiguration(), HttpVersion.HTTP_2_0);
            def tlsHandler = builder.build().newHandler(ByteBufAllocator.DEFAULT)

            serverChannel.pipeline()
                    .addLast(tlsHandler)
                    .addLast(new ApplicationProtocolNegotiationHandler("h2") {
                        @Override
                        protected void configurePipeline(ChannelHandlerContext chtx, String protocol) throws Exception {
                            chtx.pipeline()
                                    .addLast(Http2FrameCodecBuilder.forServer().build())
                        }
                    })
        }

        void setupH2c() {
            scheme = 'http'

            ChannelHandler responseStreamHandler = new ChannelInboundHandlerAdapter() {
                @Override
                void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    if (evt instanceof Http2FrameStreamEvent && evt.stream().id() == 1) {
                        h2cResponseStream = evt.stream()
                    }

                    super.userEventTriggered(ctx, evt)
                }
            }
            Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forServer()
                    .build()
            HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory = protocol -> {
                if (AsciiString.contentEquals("h2c", protocol)) {
                    return new Http2ServerUpgradeCodec(frameCodec, responseStreamHandler)
                } else {
                    return null
                }
            }

            HttpServerCodec sourceCodec = new HttpServerCodec()
            serverChannel.pipeline()
                    .addLast(new LoggingHandler(LogLevel.INFO))
                    .addLast(sourceCodec)
                    .addLast(new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory, 1024))
        }

        void exchangeSettings() {
            advance()

            assert serverChannel.readInbound() instanceof Http2SettingsFrame
            assert serverChannel.readInbound() instanceof Http2SettingsAckFrame
        }

        void exchangeH2c() {
            clientChannel.pipeline().fireChannelActive()
            advance()

            Http2HeadersFrame upgradeRequest = serverChannel.readInbound()
            assert upgradeRequest.headers().get(Http2Headers.PseudoHeaderName.METHOD.value()) == 'GET'
            assert upgradeRequest.headers().get(Http2Headers.PseudoHeaderName.PATH.value()) == '/'
            assert upgradeRequest.headers().get(Http2Headers.PseudoHeaderName.AUTHORITY.value()) == 'example.com:80'
            assert upgradeRequest.headers().get('content-length') == '0'
            // client closes the stream immediately
            assert upgradeRequest.stream().state() == Http2Stream.State.CLOSED

            assert serverChannel.readInbound() instanceof Http2SettingsFrame
            assert serverChannel.readInbound() instanceof Http2ResetFrame
            assert serverChannel.readInbound() instanceof Http2SettingsAckFrame
        }

        void respondOk(Http2FrameStream stream) {
            def responseHeaders = new DefaultHttp2Headers()
            responseHeaders.add(Http2Headers.PseudoHeaderName.STATUS.value(), "200")
            serverChannel.writeOutbound(new DefaultHttp2HeadersFrame(responseHeaders, true).stream(stream))
        }

        Future<HttpResponse<?>> testExchangeRequest(HttpClient client) {
            def future = Mono.from(client.exchange(scheme + '://example.com/foo')).toFuture()
            future.exceptionally(t -> t.printStackTrace())
            return future
        }

        void testExchangeResponse(Future<HttpResponse<?>> future) {
            Http2HeadersFrame request = serverChannel.readInbound()
            assert request.headers().get(Http2Headers.PseudoHeaderName.PATH.value()) == '/foo'
            assert request.headers().get(Http2Headers.PseudoHeaderName.SCHEME.value()) == scheme
            assert request.headers().get(Http2Headers.PseudoHeaderName.AUTHORITY.value()) == 'example.com'
            assert request.headers().get(Http2Headers.PseudoHeaderName.METHOD.value()) == 'GET'

            respondOk(request.stream())
            advance()

            def response = future.get()
            assert response.status() == HttpStatus.OK
        }

        Queue<String> testStreamingRequest(StreamingHttpClient client) {
            def responseData = new ArrayDeque<String>()
            Flux.from(client.dataStream(HttpRequest.GET(scheme + '://example.com/foo')))
                    .doOnError(t -> t.printStackTrace())
                    .doOnComplete(() -> responseData.add("END"))
                    .subscribe(b -> responseData.add(b.toString(StandardCharsets.UTF_8)))
            return responseData
        }

        void testStreamingResponse(Queue<String> responseData) {
            advance()
            Http2HeadersFrame request = serverChannel.readInbound()
            assert request.headers().get(Http2Headers.PseudoHeaderName.PATH.value()) == '/foo'
            assert request.headers().get(Http2Headers.PseudoHeaderName.SCHEME.value()) == scheme
            assert request.headers().get(Http2Headers.PseudoHeaderName.AUTHORITY.value()) == 'example.com'
            assert request.headers().get(Http2Headers.PseudoHeaderName.METHOD.value()) == 'GET'

            def responseHeaders = new DefaultHttp2Headers()
            responseHeaders.add(Http2Headers.PseudoHeaderName.STATUS.value(), "200")
            serverChannel.writeOutbound(new DefaultHttp2HeadersFrame(responseHeaders, false).stream(request.stream()))
            serverChannel.writeOutbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer('foo'.bytes)).stream(request.stream()))
            advance()

            assert responseData.poll() == 'foo'
            assert responseData.isEmpty()

            serverChannel.writeOutbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer('bar'.bytes), true).stream(request.stream()))
            advance()

            assert responseData.poll() == 'bar'
            assert responseData.poll() == 'END'
        }
    }

    @Singleton
    static class CustomizerTracker implements NettyClientCustomizer, BeanCreatedEventListener<Registry> {
        final Queue<Snapshot> initialPipelineBuilt = new ArrayDeque<>()
        final Queue<Snapshot> streamPipelineBuilt = new ArrayDeque<>()
        final Queue<Snapshot> requestPipelineBuilt = new ArrayDeque<>()

        @Override
        NettyClientCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            return new NettyClientCustomizer() {
                @Override
                NettyClientCustomizer specializeForChannel(Channel channel_, ChannelRole role_) {
                    return CustomizerTracker.this.specializeForChannel(channel_, role_)
                }

                Snapshot snap() {
                    return new Snapshot(channel, role, channel.pipeline().names())
                }

                @Override
                void onInitialPipelineBuilt() {
                    initialPipelineBuilt.add(snap())
                }

                @Override
                void onStreamPipelineBuilt() {
                    streamPipelineBuilt.add(snap())
                }

                @Override
                void onRequestPipelineBuilt() {
                    requestPipelineBuilt.add(snap())
                }
            }
        }

        @Override
        Registry onCreated(BeanCreatedEvent<Registry> event) {
            event.getBean().register(this)
            return event.getBean()
        }

        static class Snapshot {
            final Channel channel
            final ChannelRole role
            final List<String> handlerNames

            Snapshot(Channel channel, ChannelRole role, List<String> handlerNames) {
                this.channel = channel
                this.role = role
                this.handlerNames = handlerNames
            }
        }
    }
}
