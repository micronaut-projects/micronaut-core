package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpVersion
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.http.server.netty.ssl.CertificateProvidedSslBuilder
import io.micronaut.http.ssl.SslConfiguration
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelId
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPromise
import io.netty.channel.EventLoop
import io.netty.channel.ServerChannel
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpContentCompressor
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpServerUpgradeHandler
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodec
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import io.netty.handler.codec.http2.Http2FrameStreamEvent
import io.netty.handler.codec.http2.Http2Headers
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2PingFrame
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
import io.netty.util.concurrent.GenericFutureListener
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.function.Executable
import org.spockframework.runtime.model.parallel.ExecutionMode
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Execution
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

@Execution(ExecutionMode.CONCURRENT)
class ConnectionManagerSpec extends Specification {
    private static void patch(DefaultHttpClient httpClient, EmbeddedTestConnectionBase... connections) {
        httpClient.connectionManager = new ConnectionManager(httpClient.connectionManager) {
            int i = 0

            @Override
            protected ChannelFuture doConnect(DefaultHttpClient.RequestKey requestKey, ConnectionManager.CustomizerAwareInitializer channelInitializer, Thread requestingThread) {
                try {
                    channelInitializer.bootstrappedCustomizer = clientCustomizer
                    def connection = connections[i++]
                    connection.clientChannel = new EmbeddedChannel(new DummyChannelId('client' + i), connection.clientInitializer, channelInitializer) {
                        def loop

                        @Override
                        EventLoop eventLoop() {
                            if (loop == null) {
                                loop = new DelegateEventLoop(super.eventLoop()) {
                                    @Override
                                    boolean inEventLoop() {
                                        return connection.inEventLoop
                                    }
                                }
                            }
                            return loop
                        }
                    }
                    def promise = connection.clientChannel.newPromise()
                    promise.setSuccess()
                    return promise
                } catch (Throwable t) {
                    // print it immediately to make sure it's not swallowed
                    t.printStackTrace()
                    throw t
                }
            }
        }
    }

    def 'simple http2 get'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn)

        def future = conn.testExchangeRequest(client)
        conn.exchangeSettings()
        conn.testExchangeResponse(future)

        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 streaming get'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn)

        def r1 = conn.testStreamingRequest(client)
        conn.exchangeSettings()
        conn.testStreamingResponse(r1)

        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'simple http1 get'() {
        def ctx = ApplicationContext.run()
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn)

        conn.testExchangeResponse(conn.testExchangeRequest(client))

        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 get with compression'() {
        def ctx = ApplicationContext.run(['spec.name': ConnectionManagerSpec.simpleName])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        conn.serverChannel.pipeline().addLast(new HttpContentCompressor())
        patch(client, conn)

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
                'spec.name': ConnectionManagerSpec.simpleName
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn)

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
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1Tls()
        patch(client, conn)

        conn.testExchangeResponse(conn.testExchangeRequest(client))

        cleanup:
        client.close()
        ctx.close()
    }

    def 'simple h2c get'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.plaintext-mode': 'h2c',
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupH2c()
        patch(client, conn)

        def future = conn.testExchangeRequest(client)
        conn.exchangeH2c()
        conn.testExchangeResponse(future)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 streaming get'() {
        def ctx = ApplicationContext.run(['spec.name': ConnectionManagerSpec.simpleName])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn)

        conn.testStreamingResponse(conn.testStreamingRequest(client))

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 concurrent stream'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp2()
        conn1.setupHttp2Tls()
        def conn2 = new EmbeddedTestConnectionHttp2()
        conn2.setupHttp2Tls()
        patch(client, conn1, conn2)

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

        // the same connection is reused for all requests
        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 reuse'() {
        def ctx = ApplicationContext.run(['spec.name': ConnectionManagerSpec.simpleName])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn)

        conn.testExchangeResponse(conn.testExchangeRequest(client))

        Queue<String> responseData1 = conn.testStreamingRequest(client)
        conn.testStreamingResponse(responseData1)
        conn.testExchangeResponse(conn.testExchangeRequest(client))
        Queue<String> responseData = conn.testStreamingRequest(client)
        conn.testStreamingResponse(responseData)

        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 not reused after refresh'() {
        def ctx = ApplicationContext.run(['spec.name': ConnectionManagerSpec.simpleName])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp1()
        conn1.setupHttp1()
        def conn2 = new EmbeddedTestConnectionHttp1()
        conn2.setupHttp1()
        patch(client, conn1, conn2)

        conn1.testExchangeResponse(conn1.testExchangeRequest(client))
        client.connectionManager().refresh()
        conn2.testExchangeResponse(conn2.testExchangeRequest(client))

        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 plain text customization'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': ConnectionManagerSpec.simpleName])
        def client = ctx.getBean(DefaultHttpClient)
        def tracker = ctx.getBean(CustomizerTracker)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn)

        when:
        conn.testExchangeResponse(conn.testExchangeRequest(client))

        Queue<String> responseData = conn.testStreamingRequest(client)
        conn.testStreamingResponse(responseData)

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
        req1Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE)

        def req2Channel = tracker.requestPipelineBuilt.poll()
        req2Channel.channel == conn.clientChannel
        req2Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE)

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
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)
        def tracker = ctx.getBean(CustomizerTracker)

        def conn = new EmbeddedTestConnectionHttp2()
        if (secure) {
            conn.setupHttp2Tls()
        } else {
            conn.setupH2c()
        }
        patch(client, conn)

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
        req1Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE)

        def req2Channel = tracker.requestPipelineBuilt.poll()
        req2Channel.role == NettyClientCustomizer.ChannelRole.HTTP2_STREAM
        req2Channel.channel !== conn.clientChannel
        req2Channel.handlerNames.contains(ChannelPipelineCustomizer.HANDLER_MICRONAUT_HTTP_RESPONSE)

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
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn)

        // do one request
        conn.testExchangeResponse(conn.testExchangeRequest(client))
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

        assertPoolConnections(client, 0)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 exchange read timeout'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.client.read-timeout': '5s',
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn)

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

        assertPoolConnections(client, 0)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 read timeout during dispatch'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.read-timeout': '5s',
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn)

        // do one request
        conn.testExchangeResponse(conn.testExchangeRequest(client))
        conn.clientChannel.unfreezeTime()
        // wait for one part of the interval
        TimeUnit.SECONDS.sleep(2)
        conn.advance()

        // second request
        def future = Mono.from(client.exchange('http://example.com/foo', String)).toFuture()
        conn.advance()

        // todo: move to advanceTime once IdleStateHandler supports it
        // wait for the second part of the interval: below read-timeout, but together with the first sleep, above it
        TimeUnit.SECONDS.sleep(3)
        conn.advance()

        assert !future.isDone()
        conn.testExchangeResponse(future)

        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 ttl'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.connect-ttl': '100s',
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp1()
        conn1.setupHttp1()
        def conn2 = new EmbeddedTestConnectionHttp1()
        conn2.setupHttp1()
        patch(client, conn1, conn2)

        def r1 = conn1.testExchangeRequest(client)
        conn1.clientChannel.advanceTimeBy(101, TimeUnit.SECONDS)
        conn1.testExchangeResponse(r1)

        // conn1 should expire now, conn2 will be the next connection
        conn2.testExchangeResponse(conn2.testExchangeRequest(client))

        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 ttl'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.client.connect-ttl': '100s',
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp2()
        conn1.setupHttp2Tls()
        def conn2 = new EmbeddedTestConnectionHttp2()
        conn2.setupHttp2Tls()
        patch(client, conn1, conn2)

        def r1 = conn1.testExchangeRequest(client)
        conn1.exchangeSettings()
        conn1.clientChannel.advanceTimeBy(101, TimeUnit.SECONDS)
        conn1.testExchangeResponse(r1)

        // conn1 should expire now, conn2 will be the next connection
        def r2 = conn2.testExchangeRequest(client)
        conn2.exchangeSettings()
        conn2.testExchangeResponse(r2)

        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http1 pool timeout'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.connection-pool-idle-timeout': '5s',
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp1()
        conn1.setupHttp1()
        def conn2 = new EmbeddedTestConnectionHttp1()
        conn2.setupHttp1()
        patch(client, conn1, conn2)

        conn1.testExchangeResponse(conn1.testExchangeRequest(client))
        conn1.clientChannel.unfreezeTime()
        // todo: move to advanceTime once IdleStateHandler supports it
        TimeUnit.SECONDS.sleep(5)
        conn1.advance()
        // conn1 should expire now, conn2 will be the next connection
        conn2.testExchangeResponse(conn2.testExchangeRequest(client))

        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    @Unroll
    def 'websocket ssl=#secure'(boolean secure) {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.client.connect-ttl': '100s',
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        if (secure) {
            conn.setupHttp1Tls()
        } else {
            conn.setupHttp1()
        }
        conn.serverChannel.pipeline().addLast(new HttpObjectAggregator(1024))
        patch(client, conn)

        def uri = conn.scheme + "://example.com/foo"
        Mono.from(client.connect(Ws, uri)).subscribe()
        conn.advance()
        io.netty.handler.codec.http.HttpRequest req = conn.serverChannel.readInbound()
        def handshaker = new WebSocketServerHandshakerFactory(uri, null, false).newHandshaker(req)
        handshaker.handshake(conn.serverChannel, req)
        conn.advance()

        conn.serverChannel.writeOutbound(new TextWebSocketFrame('foo'))
        conn.advance()
        TextWebSocketFrame response = conn.serverChannel.readInbound()
        assert response.text() == 'received: foo'

        cleanup:
        client.close()
        ctx.close()

        where:
        secure << [true, false]
    }

    @ClientWebSocket
    static class Ws implements AutoCloseable {
        @Override
        void close() throws Exception {
        }

        @OnMessage
        def onMessage(String msg, WebSocketSession session) {
            return session.send('received: ' + msg)
        }
    }

    def 'cancel pool acquisition'() {
        def ctx = ApplicationContext.run('spec.name': ConnectionManagerSpec.simpleName)
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()

        ChannelPromise delayPromise
        def normalInit = conn.clientInitializer
        // hack: delay the channelActive call until we complete delayPromise
        conn.clientInitializer = new ChannelInitializer<EmbeddedChannel>() {
            @Override
            protected void initChannel(EmbeddedChannel ch) throws Exception {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    void channelActive(ChannelHandlerContext chtx) throws Exception {
                        delayPromise = chtx.newPromise()
                        delayPromise.addListener(new GenericFutureListener<io.netty.util.concurrent.Future<? super Void>>() {
                            @Override
                            void operationComplete(io.netty.util.concurrent.Future<? super Void> future) throws Exception {
                                chtx.fireChannelActive()
                            }
                        })
                    }
                })
                ch.pipeline().addLast(normalInit)
            }
        }

        patch(client, conn)

        def subscription = Mono.from(client.exchange(conn.scheme + '://example.com/foo')).subscribe()
        conn.advance()
        subscription.dispose()
        // this completes the handshake
        delayPromise.setSuccess()
        conn.advance()

        conn.testExchangeResponse(conn.testExchangeRequest(client))

        cleanup:
        client.close()
        ctx.close()
    }

    def 'max pending acquires'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.pool.max-pending-acquires': 5,
                'micronaut.http.client.pool.max-pending-connections': 1,
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()

        ChannelPromise delayPromise
        def normalInit = conn.clientInitializer
        // hack: delay the channelActive call until we complete delayPromise
        conn.clientInitializer = new ChannelInitializer<EmbeddedChannel>() {
            @Override
            protected void initChannel(EmbeddedChannel ch) throws Exception {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    void channelActive(ChannelHandlerContext chtx) throws Exception {
                        delayPromise = chtx.newPromise()
                        delayPromise.addListener(new GenericFutureListener<io.netty.util.concurrent.Future<? super Void>>() {
                            @Override
                            void operationComplete(io.netty.util.concurrent.Future<? super Void> future) throws Exception {
                                chtx.fireChannelActive()
                            }
                        })
                    }
                })
                ch.pipeline().addLast(normalInit)
            }
        }

        patch(client, conn)

        List<CompletableFuture<?>> futures = new ArrayList<>()
        for (int i = 0; i < 6; i++) {
            futures.add(Mono.from(client.exchange(conn.scheme + '://example.com/foo')).toFuture())
        }
        conn.advance()

        for (int i = 0; i < 5; i++) {
            assert !futures.get(i).isDone()
        }
        assert futures.get(5).isDone()
        assert futures.get(5).completedExceptionally

        cleanup:
        client.close()
        ctx.close()
    }

    def 'max http1 connections'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.pool.max-pending-connections': 1,
                'micronaut.http.client.pool.max-concurrent-http1-connections': 2,
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp1()
        conn1.setupHttp1()
        def conn2 = new EmbeddedTestConnectionHttp1()
        conn2.setupHttp1()

        patch(client, conn1, conn2)

        // we open four requests, the first two of which will open connections.
        List<CompletableFuture<HttpResponse<?>>> futures = [
                conn1.testExchangeRequest(client),
                conn2.testExchangeRequest(client),
                conn1.testExchangeRequest(client),
                conn1.testExchangeRequest(client),
        ]

        conn1.testExchangeResponse(futures.get(0))
        conn1.testExchangeResponse(futures.get(2))
        conn1.testExchangeResponse(futures.get(3))
        conn2.testExchangeResponse(futures.get(1))

        cleanup:
        client.close()
        ctx.close()
    }

    def 'multipart request'() {
        def ctx = ApplicationContext.run('spec.name': ConnectionManagerSpec.simpleName)
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn)
        conn.serverChannel.pipeline().addLast(new HttpObjectAggregator(1024))

        def future = Mono.from(client.exchange(HttpRequest.POST(conn.scheme + '://example.com/foo', MultipartBody.builder()
                .addPart('foo', 'fn', MediaType.TEXT_PLAIN_TYPE, 'bar'.bytes)
                .build())
                .contentType(MediaType.MULTIPART_FORM_DATA), String)).toFuture()
        future.exceptionally(t -> t.printStackTrace())
        conn.advance()

        FullHttpRequest request = conn.serverChannel.readInbound()
        assert request.uri() == '/foo'
        assert request.method() == HttpMethod.POST
        assert request.headers().get('host') == 'example.com'
        assert request.headers().get("connection") == "keep-alive"
        assert request.content().isReadable(100) // cba to check the exact content

        def response = new DefaultFullHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer('foo'.bytes))
        response.headers().add("Content-Length", 3)
        conn.serverChannel.writeOutbound(response)
        conn.advance()
        assert future.get().body() == 'foo'

        cleanup:
        client.close()
        ctx.close()
    }

    def 'publisher request'() {
        def ctx = ApplicationContext.run('spec.name': ConnectionManagerSpec.simpleName)
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp1()
        conn.setupHttp1()
        patch(client, conn)
        conn.serverChannel.pipeline().addLast(new HttpObjectAggregator(1024))

        def future = Mono.from(client.exchange(HttpRequest.POST(conn.scheme + '://example.com/foo', Flux.fromIterable([1,2,3,4,5]))
                .contentType(MediaType.APPLICATION_JSON_TYPE), String)).toFuture()
        future.exceptionally(t -> t.printStackTrace())
        conn.advance()

        FullHttpRequest request = conn.serverChannel.readInbound()
        assert request.uri() == '/foo'
        assert request.method() == HttpMethod.POST
        assert request.headers().get('host') == 'example.com'
        assert request.headers().get("connection") == "keep-alive"
        assert request.content().toString(StandardCharsets.UTF_8) == '[1,2,3,4,5]'

        def response = new DefaultFullHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer('foo'.bytes))
        response.headers().add("Content-Length", 3)
        conn.serverChannel.writeOutbound(response)
        conn.advance()
        assert future.get().body() == 'foo'

        cleanup:
        client.close()
        ctx.close()
    }

    def 'connection pool disabled http1'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.pool.enabled': false,
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp1()
        conn1.setupHttp1()
        def conn2 = new EmbeddedTestConnectionHttp1()
        conn2.setupHttp1()
        patch(client, conn1, conn2)

        def r1 = conn1.testExchangeRequest(client)
        conn1.testExchangeResponse(r1, "close")

        def r2 = conn2.testExchangeRequest(client)
        conn2.testExchangeResponse(r2, "close")

        assertPoolConnections(client, 0)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'connection pool disabled http2'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.pool.enabled': false,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp2()
        conn1.setupHttp2Tls()
        def conn2 = new EmbeddedTestConnectionHttp2()
        conn2.setupHttp2Tls()
        patch(client, conn1, conn2)

        def r1 = conn1.testExchangeRequest(client)
        conn1.exchangeSettings()
        conn1.testExchangeResponse(r1)

        def r2 = conn2.testExchangeRequest(client)
        conn2.exchangeSettings()
        conn2.testExchangeResponse(r2)

        assertPoolConnections(client, 0)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 goaway'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates'  : true,
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn1 = new EmbeddedTestConnectionHttp2()
        conn1.setupHttp2Tls()
        def conn2 = new EmbeddedTestConnectionHttp2()
        conn2.setupHttp2Tls()
        patch(client, conn1, conn2)

        def future = conn1.testExchangeRequest(client)
        conn1.exchangeSettings()
        conn1.testExchangeResponse(future)

        // use NO_ERROR so that netty doesn't close the channel
        conn1.serverChannel.writeOutbound(new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR, Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)))
        conn1.advance()

        // after goaway, new requests should use a new connection
        def future2 = conn2.testExchangeRequest(client)
        conn2.exchangeSettings()
        conn2.testExchangeResponse(future2)

        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'http2 channel inactive but fire inactive channel scheduled after acquire'() {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.client.read-timeout': '5s',
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        def conn2 = new EmbeddedTestConnectionHttp2()
        conn2.setupHttp2Tls()
        patch(client, conn, conn2)

        // The channel inactive event is scheduled to be fired in the event loop
        // after the channel is already inactive. If code running in the event loop
        // relies on the event being received to finish, it will block forever.

        // first request
        def future = conn.testExchangeRequest(client)
        conn.exchangeSettings()
        // this closes the channel, but doesn't fire channel inactive
        conn.clientChannel.unsafe().closeForcibly()

        // second request
        Executable executable = () -> {
            // this would block forever before the fix
            def future2 = conn2.testExchangeRequest(client)
            // this will fire channel inactive
            conn.clientChannel.close()
            conn2.exchangeSettings()
            conn2.testExchangeResponse(future2)
        };
        Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), executable);

        // first request times out as channel was closed
        when:
        future.get()
        then:
        def e = thrown ExecutionException
        e.cause instanceof ReadTimeoutException

        assertPoolConnections(client, 1)

        cleanup:
        client.close()
        ctx.close()
    }

    def 'timeout before dispatch0'(boolean http2) {
        def ctx = ApplicationContext.run([
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.client.read-timeout': '1s',
                'spec.name': ConnectionManagerSpec.simpleName,
        ])
        def client = ctx.getBean(DefaultHttpClient)

        def conn
        if (http2) {
            conn = new EmbeddedTestConnectionHttp2()
            conn.setupHttp2Tls()
        } else {
            conn = new EmbeddedTestConnectionHttp1()
            conn.setupHttp1()
        }
        patch(client, conn)

        // do one request
        def r1 = conn.testExchangeRequest(client)
        if (http2) {
            conn.exchangeSettings()
        }
        conn.testExchangeResponse(r1)
        conn.clientChannel.unfreezeTime()
        // trigger timeout
        TimeUnit.SECONDS.sleep(2)

        // second request
        // this triggers the dispatch0 logic to be delayed with execute
        conn.inEventLoop = false
        def future = Mono.from(client.exchange(conn.scheme + '://example.com/foo')).toFuture()
        future.exceptionally(t -> t.printStackTrace())
        conn.inEventLoop = true
        conn.clientChannel.runScheduledPendingTasks()
        conn.advance()
        conn.testExchangeResponse(future)

        cleanup:
        client.close()
        ctx.close()

        where:
        http2 << [false, true]
    }

    def 'automated ping, writes only'(String prop, boolean ping) {
        given:
        def props = [
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'spec.name': ConnectionManagerSpec.simpleName,
        ]
        props.put(prop, '1s')
        def ctx = ApplicationContext.run(props)
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn)

        // do one request
        def r1 = conn.testExchangeRequest(client)
        conn.exchangeSettings()
        conn.testExchangeResponse(r1)

        conn.clientChannel.unfreezeTime()
        for (int i = 0; i < 8; i++) {
            conn.testExchangeRequest(client)
            TimeUnit.MILLISECONDS.sleep(250)
        }
        conn.advance()

        def msgs = conn.serverChannel.inboundMessages().toList()

        expect:
        msgs.size() >= (ping ? 9 : 8)
        msgs.any { it instanceof Http2PingFrame } == ping

        cleanup:
        client.close()
        ctx.close()

        where:
        prop                                              | ping
        'micronaut.http.client.http2.ping-interval-read'  | true
        'micronaut.http.client.http2.ping-interval-write' | false
        'micronaut.http.client.http2.ping-interval-idle'  | false
    }

    def 'automated ping, no traffic'(String prop, boolean ping) {
        given:
        def props = [
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'spec.name': ConnectionManagerSpec.simpleName,
        ]
        props.put(prop, '1s')
        def ctx = ApplicationContext.run(props)
        def client = ctx.getBean(DefaultHttpClient)

        def conn = new EmbeddedTestConnectionHttp2()
        conn.setupHttp2Tls()
        patch(client, conn)

        // do one request
        def r1 = conn.testExchangeRequest(client)
        conn.exchangeSettings()
        conn.testExchangeResponse(r1)

        conn.clientChannel.unfreezeTime()
        TimeUnit.SECONDS.sleep(2)
        conn.advance()

        expect:
        conn.serverChannel.readInbound() instanceof Http2PingFrame == ping

        cleanup:
        client.close()
        ctx.close()

        where:
        prop                                              | ping
        'micronaut.http.client.http2.ping-interval-read'  | true
        'micronaut.http.client.http2.ping-interval-write' | true
        'micronaut.http.client.http2.ping-interval-idle'  | true
    }

    void assertPoolConnections(DefaultHttpClient client, int count) {
        assert client.connectionManager.getChannels().size() == count
        client.connectionManager.getChannels().forEach { assert it.isActive() }
    }

    static class EmbeddedTestConnectionBase {
        final EmbeddedChannel serverChannel
        EmbeddedChannel clientChannel
        ChannelInitializer<EmbeddedChannel> clientInitializer = new ChannelInitializer<EmbeddedChannel>() {
            @Override
            protected void initChannel(EmbeddedChannel ch) throws Exception {
                ch.freezeTime()
                ch.config().setAutoRead(false)
                EmbeddedTestUtil.connect(serverChannel, ch)
            }
        }
        boolean inEventLoop = true

        EmbeddedTestConnectionBase() {
            serverChannel = new EmbeddedServerChannel(new DummyChannelId('server'))
            serverChannel.freezeTime()
            serverChannel.config().setAutoRead(true)
        }

        final void advance() {
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

        CompletableFuture<HttpResponse<?>> testExchangeRequest(HttpClient client) {
            def future = Mono.from(client.exchange(scheme + '://example.com/foo')).toFuture()
            future.exceptionally(t -> t.printStackTrace())
            advance()
            return future
        }

        void testExchangeResponse(CompletableFuture<HttpResponse<?>> future, String connectionHeader = "keep-alive") {
            io.netty.handler.codec.http.HttpRequest request = serverChannel.readInbound()
            assert request.uri() == '/foo'
            assert request.method() == HttpMethod.GET
            assert request.headers().get('host') == 'example.com'
            assert request.headers().get("connection") == connectionHeader

            def tail = serverChannel.readInbound()
            assert tail == null || tail instanceof LastHttpContent

            respondOk()
            advance()

            assert future.get().status() == HttpStatus.OK
        }

        private Queue<String> testStreamingRequest(StreamingHttpClient client) {
            def responseData = new ArrayDeque<String>()
            Flux.from(client.dataStream(HttpRequest.GET(scheme + '://example.com/foo')))
                    .doOnError(t -> t.printStackTrace())
                    .doOnComplete(() -> responseData.add("END"))
                    .subscribe(b -> responseData.add(b.toString(StandardCharsets.UTF_8)))
            responseData
        }

        private void testStreamingResponse(Queue<String> responseData) {
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
            assert responseData.isEmpty()

            serverChannel.writeOutbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer('bar'.bytes)))
            advance()

            assert responseData.poll() == 'bar'
            assert responseData.poll() == 'END'
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

            assert future.isDone()
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

    @Requires(property = "spec.name", value = "ConnectionManagerSpec")
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
