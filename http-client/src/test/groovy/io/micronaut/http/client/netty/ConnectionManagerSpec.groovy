package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpVersion
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.server.netty.ssl.CertificateProvidedSslBuilder
import io.micronaut.http.ssl.SslConfiguration
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import io.netty.handler.codec.http2.Http2Headers
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2SettingsAckFrame
import io.netty.handler.codec.http2.Http2SettingsFrame
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.SelfSignedCertificate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets

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
        given:
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn.clientChannel)

        when:
        def future = Mono.from(client.exchange('https://example.com/foo')).toFuture()
        future.exceptionally(t -> t.printStackTrace())
        conn.exchangeSettings()
        then:
        Http2HeadersFrame request = conn.serverChannel.readInbound()
        request.headers().get(Http2Headers.PseudoHeaderName.PATH.value()) == '/foo'
        request.headers().get(Http2Headers.PseudoHeaderName.SCHEME.value()) == 'https'
        request.headers().get(Http2Headers.PseudoHeaderName.AUTHORITY.value()) == 'example.com'
        request.headers().get(Http2Headers.PseudoHeaderName.METHOD.value()) == 'GET'

        when:
        conn.respondOk(request.stream())
        conn.advance()
        then:
        def response = future.get()
        response.status() == HttpStatus.OK

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 streaming get'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn.clientChannel)

        when:
        def responseData = new ArrayDeque<String>()
        def responseComplete = false
        Flux.from(client.dataStream(HttpRequest.GET('https://example.com/foo')))
            .doOnError(t -> t.printStackTrace())
            .doOnComplete(() -> responseComplete = true)
            .subscribe(b -> responseData.add(b.toString(StandardCharsets.UTF_8)))
        conn.exchangeSettings()
        then:
        Http2HeadersFrame request = conn.serverChannel.readInbound()
        request.headers().get(Http2Headers.PseudoHeaderName.PATH.value()) == '/foo'
        request.headers().get(Http2Headers.PseudoHeaderName.SCHEME.value()) == 'https'
        request.headers().get(Http2Headers.PseudoHeaderName.AUTHORITY.value()) == 'example.com'
        request.headers().get(Http2Headers.PseudoHeaderName.METHOD.value()) == 'GET'

        when:
        def responseHeaders = new DefaultHttp2Headers()
        responseHeaders.add(Http2Headers.PseudoHeaderName.STATUS.value(), "200")
        conn.serverChannel.writeOutbound(new DefaultHttp2HeadersFrame(responseHeaders, false).stream(request.stream()))
        conn.serverChannel.writeOutbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer('foo'.bytes)).stream(request.stream()))
        conn.advance()
        then:
        responseData.poll() == 'foo'
        !responseComplete

        when:
        conn.serverChannel.writeOutbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer('bar'.bytes), true).stream(request.stream()))
        conn.advance()
        then:
        responseData.poll() == 'bar'
        responseComplete

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

    static class EmbeddedTestConnectionBase {
        final EmbeddedChannel serverChannel
        final EmbeddedChannel clientChannel

        EmbeddedTestConnectionBase() {
            serverChannel = new EmbeddedChannel(new DummyChannelId('server'))
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
            def future = Mono.from(client.exchange(scheme + '://example.com/foo')).toFuture()
            future.exceptionally(t -> t.printStackTrace())
            advance()

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
        void setupHttp2Tls() {
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
                                    .addLast(Http2FrameCodecBuilder.forServer()
                                            .autoAckSettingsFrame(false)
                                            .build())
                        }
                    })
        }

        void exchangeSettings() {
            advance()
            if (!(serverChannel.readInbound() instanceof Http2SettingsFrame)) {
                throw new AssertionError()
            }
            if (!(serverChannel.readInbound() instanceof Http2SettingsAckFrame)) {
                throw new AssertionError()
            }
        }

        void respondOk(Http2FrameStream stream) {
            def responseHeaders = new DefaultHttp2Headers()
            responseHeaders.add(Http2Headers.PseudoHeaderName.STATUS.value(), "200")
            serverChannel.writeOutbound(new DefaultHttp2HeadersFrame(responseHeaders, true).stream(stream))
        }
    }
}
