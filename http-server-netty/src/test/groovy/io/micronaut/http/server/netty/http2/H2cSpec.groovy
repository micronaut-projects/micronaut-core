package io.micronaut.http.server.netty.http2

import io.micronaut.context.annotation.Property
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpClientUpgradeHandler
import io.netty.handler.codec.http.HttpMessage
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2Connection
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec
import io.netty.handler.codec.http2.HttpConversionUtil
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder
import io.netty.util.ReferenceCountUtil
import jakarta.inject.Inject
import org.jetbrains.annotations.NotNull
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.PendingFeature
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@MicronautTest
@Property(name = "micronaut.server.http-version", value = "2.0")
//@Property(name = "micronaut.server.port", value = "8912")
@Property(name = "micronaut.http.client.http-version", value = "2.0")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/5005')
class H2cSpec extends Specification {
    @Inject
    EmbeddedServer embeddedServer

    @Inject
    HttpClient httpClient

    @Inject
    StreamingHttpClient streamingHttpClient

    private CompletableFuture requestUpgrade(DefaultFullHttpRequest initialRequest) {
        def responseFuture = new CompletableFuture()
        def bootstrap = new Bootstrap()
                .remoteAddress(embeddedServer.host, embeddedServer.port)
                .group(new NioEventLoopGroup())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(@NotNull SocketChannel ch) throws Exception {
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
                        def upgradeCodec = new Http2ClientUpgradeCodec(ChannelPipelineCustomizer.HANDLER_HTTP2_CONNECTION, connectionHandler)
                        def upgradeHandler = new HttpClientUpgradeHandler(clientCodec, upgradeCodec, 1000000)

                        ch.pipeline()
                                .addLast(ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC, clientCodec)
                                .addLast(upgradeHandler)
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
                                        ctx.read()
                                        if (msg instanceof HttpMessage) {
                                            if (msg.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), -1) != 1) {
                                                responseFuture.completeExceptionally(new AssertionError("Response must be on stream 1"));
                                            }
                                            responseFuture.complete(ReferenceCountUtil.retain(msg))
                                        }
                                        super.channelRead(ctx, msg)
                                    }

                                    @Override
                                    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                        super.exceptionCaught(ctx, cause)
                                        cause.printStackTrace()
                                        responseFuture.completeExceptionally(cause)
                                    }
                                })

                    }
                })

        def channel = (SocketChannel) bootstrap.connect().await().channel()

        channel.writeAndFlush(initialRequest)
        channel.read()

        return responseFuture
    }

    void 'test using direct netty http2 client'() {
        given:
        def responseFuture = requestUpgrade(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/h2c/test'))

        expect:
        responseFuture.get(10, TimeUnit.SECONDS) != null
    }

    void 'test using micronaut http client: retrieve'() {
        expect:
        httpClient.toBlocking().retrieve("http://localhost:${embeddedServer.port}/h2c/test") == 'foo'
        httpClient.toBlocking().retrieve("http://localhost:${embeddedServer.port}/h2c/testStream") == 'foo'
    }

    void 'test using micronaut http client: retrieve reverse'() {
        // order matters because the client reuses connections
        expect:
        httpClient.toBlocking().retrieve("http://localhost:${embeddedServer.port}/h2c/testStream") == 'foo'
        httpClient.toBlocking().retrieve("http://localhost:${embeddedServer.port}/h2c/test") == 'foo'
    }

    private def stream(String url) {
        def composed = Unpooled.buffer()
        def future = new CompletableFuture()
        streamingHttpClient.dataStream(HttpRequest.GET(url)).subscribe(new Subscriber<ByteBuffer<?>>() {
            @Override
            void onSubscribe(Subscription s) {
            }

            @Override
            void onNext(ByteBuffer<?> byteBuffer) {
                composed.writeBytes(byteBuffer.toByteArray())
            }

            @Override
            void onError(Throwable t) {
                future.completeExceptionally(t)
            }

            @Override
            void onComplete() {
                future.complete(null)
            }
        })
        future.get()
        return composed.toString(StandardCharsets.UTF_8)
    }

    @PendingFeature
    @Ignore
    // todo: streaming h2c is currently broken. This is because addFinalHandler is called after the stream receivers
    //  have been registered to the pipeline. This means that http2 messages aren't transformed to http messages
    //  properly.
    void 'test using micronaut http client: stream'() {
        expect:
        stream("http://localhost:${embeddedServer.port}/h2c/test") == 'foo'
        stream("http://localhost:${embeddedServer.port}/h2c/testStream") == 'foo'
    }

    @PendingFeature
    @Ignore
    void 'test using micronaut http client: stream reverse'() {
        // order matters because the client reuses connections
        expect:
        stream("http://localhost:${embeddedServer.port}/h2c/testStream") == 'foo'
        stream("http://localhost:${embeddedServer.port}/h2c/test") == 'foo'
    }

    def 'http1.1 put'() {
        given:
        def http1Client = HttpClient.create(new URL("http://localhost:${embeddedServer.port}/"))

        expect:
        http1Client.toBlocking().exchange(HttpRequest.PUT("http://localhost:${embeddedServer.port}/h2c/put", "foo"), String).body() == 'Example response: foo'
    }

    void 'test using direct netty http2 client: body in initial request'() {
        given:
        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, '/h2c/put', Unpooled.wrappedBuffer('foo'.getBytes(StandardCharsets.UTF_8)))
        request.headers().add("Content-Length", 3)

        CompletableFuture responseFuture = requestUpgrade(request)

        when:
        def content = ((FullHttpResponse) responseFuture.get(10, TimeUnit.SECONDS)).content()

        then:
        content.toString(StandardCharsets.UTF_8) == 'Example response: foo'

        cleanup:
        content.release()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6299')
    void 'test using direct netty http2 client: body in initial request with json parsing'() {
        given:
        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, '/h2c/putJson', Unpooled.wrappedBuffer('{"title":"foo"}'.getBytes(StandardCharsets.UTF_8)))
        request.headers().add("Content-Length", '{"title":"foo"}'.length())
        request.headers().add("Content-Type", 'application/json')

        CompletableFuture responseFuture = requestUpgrade(request)

        when:
        def content = ((FullHttpResponse) responseFuture.get(10, TimeUnit.SECONDS)).content()

        then:
        content.toString(StandardCharsets.UTF_8) == 'Example response: foo'

        cleanup:
        content.release()
    }

    @Controller("/h2c")
    static class TestController {
        @Get("/test")
        String test(HttpRequest<?> request) {
            if (request.httpVersion != io.micronaut.http.HttpVersion.HTTP_2_0) {
                throw new IllegalArgumentException('Request should be HTTP 2.0')
            }
            return 'foo'
        }

        @Get("/testStream")
        Publisher<byte[]> testStream(HttpRequest<?> request) {
            if (request.httpVersion != io.micronaut.http.HttpVersion.HTTP_2_0) {
                throw new IllegalArgumentException('Request should be HTTP 2.0')
            }
            return Flux.create {sink ->
                new Thread({
                    sink.next("f".getBytes(StandardCharsets.UTF_8))
                    TimeUnit.SECONDS.sleep(1)
                    sink.next("oo".getBytes(StandardCharsets.UTF_8))
                    sink.complete()
                }).start()
            }
        }

        @Put('/put')
        String put(@Body String body) {
            return "Example response: $body"
        }

        @Put('/putJson')
        String putJson(@Body Book body) {
            return "Example response: $body.title"
        }

        static class Book {
            String title
        }
    }
}
