package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2DataFrame
import io.netty.handler.codec.http2.Http2FrameCodec
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2StreamFrame
import io.netty.util.ReferenceCountUtil
import org.jspecify.annotations.NonNull
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * An HTTP/2 request with {@code Expect: 100-continue} must get its {@code 100 Continue} promptly
 * when the application subscribes to the body after the inbound read has completed, not only
 * when it subscribes while the request headers are being read.
 */
class Http2DelayedContinueSpec extends Specification {
    static final long DELAY_MS = 500
    /**
     * How long the client waits for the {@code 100 Continue} before sending the body anyway,
     * like a client's continue fallback timer.
     */
    static final long CLIENT_FALLBACK_MS = 5000
    /**
     * Slack on top of the subscription delay for the {@code 100 Continue} to reach the client.
     */
    static final long MARGIN_MS = 2000

    def "100 Continue arrives promptly (path=#path, legacy=#legacy)"(String path, boolean legacy, long expectedDelay) {
        given:
        def server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                       : 'Http2DelayedContinueSpec',
                'micronaut.server.http-version'                   : '2.0',
                'micronaut.server.ssl.enabled'                    : false,
                'micronaut.server.netty.legacy-multiplex-handlers': legacy,
        ])
        def group = new NioEventLoopGroup(1)
        def continueReceived = new CompletableFuture<Long>()
        def response = new CompletableFuture<String>()
        def status = new CompletableFuture<String>()
        def responseBody = new StringBuilder()
        def codec = Http2FrameCodecBuilder.forClient().build()
        def channel = new Bootstrap()
                .remoteAddress(server.host, server.port)
                .group(group)
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(@NonNull SocketChannel ch) {
                        ch.pipeline().addLast(codec, new ChannelInboundHandlerAdapter() {
                            @Override
                            void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) {
                                try {
                                    if (msg instanceof Http2HeadersFrame) {
                                        def s = msg.headers().status().toString()
                                        if (s == HttpResponseStatus.CONTINUE.codeAsText().toString()) {
                                            continueReceived.complete(System.nanoTime())
                                        } else {
                                            status.complete(s)
                                            if (msg.isEndStream()) {
                                                response.complete(responseBody.toString())
                                            }
                                        }
                                    } else if (msg instanceof Http2DataFrame) {
                                        responseBody.append(msg.content().toString(StandardCharsets.UTF_8))
                                        if (msg.isEndStream()) {
                                            response.complete(responseBody.toString())
                                        }
                                    }
                                } finally {
                                    ReferenceCountUtil.release(msg)
                                }
                            }

                            @Override
                            void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                continueReceived.completeExceptionally(cause)
                                response.completeExceptionally(cause)
                            }
                        })
                    }
                })
                .connect().sync().channel()

        when:
        def stream = codec.newStream()
        def headers = new DefaultHttp2Headers()
                .method('POST')
                .path(path)
                .scheme('http')
                .authority("$server.host:$server.port")
        headers.set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
        headers.set(HttpHeaderNames.CONTENT_TYPE, 'text/plain')
        long sent = System.nanoTime()
        channel.writeAndFlush(new DefaultHttp2HeadersFrame(headers, false).stream(stream)).sync()
        long continueAfterMs
        try {
            continueAfterMs = TimeUnit.NANOSECONDS.toMillis(continueReceived.get(CLIENT_FALLBACK_MS, TimeUnit.MILLISECONDS) - sent)
        } catch (TimeoutException ignored) {
            // no 100 Continue: fall back to sending the body, like a client would
            continueAfterMs = -1
        }
        channel.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer('foo', StandardCharsets.UTF_8), true).stream(stream)).sync()

        then:
        continueAfterMs >= 0
        continueAfterMs < expectedDelay + MARGIN_MS
        status.get(10, TimeUnit.SECONDS) == '200'
        response.get(10, TimeUnit.SECONDS) == 'foo'

        cleanup:
        channel?.close()?.sync()
        group.shutdownGracefully()
        server.close()

        where:
        path                         | legacy | expectedDelay
        '/h2-delayed-continue/now'   | false  | 0
        '/h2-delayed-continue/later' | false  | DELAY_MS
        '/h2-delayed-continue/now'   | true   | 0
        '/h2-delayed-continue/later' | true   | DELAY_MS
    }

    @Requires(property = 'spec.name', value = 'Http2DelayedContinueSpec')
    @Controller('/h2-delayed-continue')
    static class DelayedContinueController {
        @Post(value = '/now', consumes = 'text/plain', produces = 'text/plain')
        Publisher<String> now(@Body Publisher<byte[]> body) {
            return join(body)
        }

        @Post(value = '/later', consumes = 'text/plain', produces = 'text/plain')
        Publisher<String> later(@Body Publisher<byte[]> body) {
            // subscribe to the body only after the inbound read has completed
            return Mono.delay(Duration.ofMillis(DELAY_MS)).then(join(body))
        }

        private static Mono<String> join(Publisher<byte[]> body) {
            return Flux.from(body)
                    .map(b -> new String(b, StandardCharsets.UTF_8))
                    .reduce('', String::concat)
        }
    }
}
