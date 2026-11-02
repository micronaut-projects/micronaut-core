package io.micronaut.http.server.netty.http2

import java.util.concurrent.CompletableFuture
import reactor.core.publisher.Flux
import org.reactivestreams.Publisher
import io.micronaut.http.annotation.Body
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.netty.RoutingInBoundHandler
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.http3.DefaultHttp3DataFrame
import io.netty.handler.codec.http3.DefaultHttp3Headers
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame
import io.netty.handler.codec.http3.Http3
import io.netty.handler.codec.http3.Http3ClientConnectionHandler
import io.netty.handler.codec.http3.Http3DataFrame
import io.netty.handler.codec.http3.Http3HeadersFrame
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.codec.quic.QuicSslContextBuilder
import io.netty.handler.codec.quic.QuicStreamChannel
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * HTTP/3 stream and connection lifecycle.
 */
class Http3StreamLifecycleSpec extends Specification {

    NioEventLoopGroup loop
    EmbeddedServer server

    def cleanup() {
        loop?.shutdownGracefully()
        server?.close()
    }

    private EmbeddedServer startServer(Map<String, Object> extra = [:]) {
        server = ApplicationContext.<EmbeddedServer> run(EmbeddedServer, [
                'spec.name'                                 : 'Http3StreamLifecycleSpec',
                'micronaut.server.ssl.enabled'              : true,
                'micronaut.server.ssl.build-self-signed'    : true,
                'micronaut.server.netty.listeners.h3.family': 'QUIC',
                'micronaut.server.netty.listeners.h3.port'  : -1,
        ] + extra)
        return server
    }

    private QuicChannel connect() {
        loop = new NioEventLoopGroup(1)
        def ch = new Bootstrap()
                .group(loop)
                .channel(NioDatagramChannel)
                .handler(Http3.newQuicClientCodecBuilder()
                        .initialMaxData(10_000_000)
                        .initialMaxStreamDataBidirectionalLocal(1_000_000)
                        .sslContext(QuicSslContextBuilder.forClient()
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .applicationProtocols(Http3.supportedApplicationProtocols()).build())
                        .build())
                .bind(0).sync().channel()
        return QuicChannel.newBootstrap(ch)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel c) throws Exception {
                        c.pipeline().addLast(new Http3ClientConnectionHandler())
                    }
                })
                .remoteAddress(new InetSocketAddress('127.0.0.1', server.port))
                .connect().get(10, TimeUnit.SECONDS)
    }

    static class StreamState extends Http3RequestStreamInboundHandler {
        volatile String status
        volatile boolean inputClosed
        volatile Throwable error

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
            status = frame.headers().status()?.toString()
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
            frame.release()
        }

        @Override
        protected void channelInputClosed(ChannelHandlerContext ctx) {
            inputClosed = true
        }

        @Override
        void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            error = cause
        }
    }

    def 'an idle HTTP/3 connection is closed after the idle timeout'() {
        given:
        startServer(['micronaut.server.idle-timeout': '1s'])
        def qc = connect()

        expect:
        qc.isActive()
        new PollingConditions(timeout: 8).eventually {
            !qc.isActive()
        }
    }

    def 'a malformed HTTP/3 request head is answered or reset, and CR/LF is not logged'() {
        given:
        def logger = (Logger) LoggerFactory.getLogger(RoutingInBoundHandler)
        def appender = new ListAppender()
        appender.start()
        logger.addAppender(appender)
        startServer()
        def qc = connect()
        def state = new StreamState()
        QuicStreamChannel stream = Http3.newRequestStream(qc, state).get()

        when:
        stream.writeAndFlush(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers(false)
                .path('/h3-lifecycle/hello?x=1\r\nFORGED-LINE').method('GET').authority('localhost').scheme('https')))
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync()

        then: 'the stream gets a 4xx answer or is reset'
        new PollingConditions(timeout: 5).eventually {
            assert state.status?.startsWith('4') || state.error != null || !stream.isActive()
        }
        and: "a well-formed request on the same connection is still served"
        def control = new StreamState()
        QuicStreamChannel controlStream = Http3.newRequestStream(qc, control).get()
        controlStream.writeAndFlush(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers()
                .path("/h3-lifecycle/hello").method("GET").authority("localhost").scheme("https")))
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync()
        new PollingConditions(timeout: 5).eventually {
            assert control.status == "200"
        }
        and: 'no raw CR/LF from :path reached the log'
        appender.messages.every { !it.contains('\nFORGED-LINE') }

        cleanup:
        logger.detachAppender(appender)
    }

    def 'HTTP/3 streams closed before the request FIN do not retain their stream slot'() {
        given:
        startServer([
                'micronaut.server.netty.keep-alive-on-server-error'                  : false,
                'micronaut.server.netty.http3.initial-max-streams-bidirectional': 4,
        ])
        def qc = connect()
        int answered = 0

        when: 'more unfinished uploads than the stream limit are answered with 500'
        for (int i = 0; i < 8; i++) {
            def state = new StreamState()
            def future = Http3.newRequestStream(qc, state)
            future.await(5, TimeUnit.SECONDS)
            if (!future.isSuccess()) {
                break
            }
            QuicStreamChannel stream = future.getNow()
            stream.write(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers()
                    .path('/h3-lifecycle/fail').method('POST').authority('localhost').scheme('https')
                    .add('content-length', '100000')))
            stream.writeAndFlush(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(new byte[1000])))
            def deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (state.status == null && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(20)
            }
            if (state.status == null) {
                break
            }
            answered++
        }

        then:
        answered == 8
    }

    def 'HTTP/3 streams answered before the request FIN do not retain their stream slot with the default config'() {
        given:
        startServer([
                'micronaut.server.netty.http3.initial-max-streams-bidirectional': 4,
        ])
        def qc = connect()
        int answered = 0

        when: 'more unfinished uploads than the stream limit are answered by routes that do not read the body'
        for (int i = 0; i < 8; i++) {
            def state = new StreamState()
            def future = Http3.newRequestStream(qc, state)
            future.await(5, TimeUnit.SECONDS)
            if (!future.isSuccess()) {
                break
            }
            QuicStreamChannel stream = future.getNow()
            stream.write(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers()
                    .path(path).method('POST').authority('localhost').scheme('https')
                    .add('content-length', '100000')))
            stream.writeAndFlush(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(new byte[1000])))
            def deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (state.status == null && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(20)
            }
            if (state.status == null) {
                break
            }
            answered++
        }

        then:
        answered == 8

        where:
        path << ['/h3-lifecycle/fail', '/h3-lifecycle/ignore-body', '/h3-lifecycle/missing']
    }

    def 'an HTTP/3 request body the app still reads after the response is not stopped'() {
        given:
        startServer()
        Ctrl.received = new CompletableFuture<>()
        def qc = connect()
        def state = new StreamState()
        QuicStreamChannel stream = Http3.newRequestStream(qc, state).get(5, TimeUnit.SECONDS)

        when: 'the route answers 202 and reads the body in the background'
        stream.write(new DefaultHttp3HeadersFrame(new DefaultHttp3Headers()
                .path('/h3-lifecycle/background').method('POST').authority('localhost').scheme('https')
                .add('content-length', '3000')))
        stream.writeAndFlush(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(new byte[1000]))).sync()
        new PollingConditions(timeout: 5).eventually {
            assert state.status == '202'
        }
        // let the input shutdown, if any, reach the client
        TimeUnit.MILLISECONDS.sleep(200)
        stream.writeAndFlush(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(new byte[2000])))
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)

        then: 'the rest of the body still reaches the route'
        Ctrl.received.get(5, TimeUnit.SECONDS) == 3000L
    }

    static class ListAppender extends AppenderBase<ILoggingEvent> {
        final List<String> messages = new CopyOnWriteArrayList<>()

        @Override
        protected void append(ILoggingEvent e) {
            messages.add(e.formattedMessage)
        }
    }

    @Controller('/h3-lifecycle')
    @Requires(property = 'spec.name', value = 'Http3StreamLifecycleSpec')
    static class Ctrl {
        @Get('/hello')
        String hello() {
            return 'hello'
        }

        @Post('/fail')
        HttpResponse<?> fail() {
            return HttpResponse.serverError()
        }

        @Post('/ignore-body')
        String ignoreBody() {
            return 'ignored'
        }

        static volatile CompletableFuture<Long> received = new CompletableFuture<>()

        @Post('/background')
        HttpResponse<?> background(@Body Publisher<byte[]> body) {
            Flux.from(body).reduce(0L, (Long n, byte[] b) -> n + b.length)
                    .subscribe({ received.complete(it) }, { received.completeExceptionally(it) })
            return HttpResponse.accepted()
        }
    }
}
