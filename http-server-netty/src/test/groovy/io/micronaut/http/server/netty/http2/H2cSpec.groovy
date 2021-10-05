package io.micronaut.http.server.netty.http2

import io.micronaut.context.annotation.Property
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
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
import jakarta.inject.Inject
import org.jetbrains.annotations.NotNull
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@MicronautTest
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.port", value = "8912")
@Property(name = "micronaut.ssl.enabled", value = "false")
@Requires({ jvm.current.isJava11Compatible() })
class H2cSpec extends Specification {
    @Inject
    EmbeddedServer embeddedServer;

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/5005')
    void 'test http2 over clear text is supported even when request data is only sent once'() {
        given:
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
                                        responseFuture.complete(msg)
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

        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/h2c/test')
        channel.writeAndFlush(request)
        channel.read()

        expect:
        responseFuture.get(10, TimeUnit.SECONDS) != null
    }

    @Controller("/h2c")
    static class TestController {
        @Get("/test")
        String test() {
            return 'foo'
        }
    }
}
