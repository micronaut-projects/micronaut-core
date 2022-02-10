package io.micronaut.http.server.netty

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpVersion
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.jetbrains.annotations.NotNull
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

@MicronautTest
@Property(name = 'spec.name', value = 'HttpPipeliningSpec')
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/4336')
class HttpPipeliningSpec extends Specification {
    @Inject
    EmbeddedServer embeddedServer

    private def makeBlockRequest(String body) {
        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/block', Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8)))
        request.headers().add(HttpHeaderNames.CONTENT_LENGTH, body.length())
        request.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        return request
    }

    private def makeSimpleRequest() {
        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/plain')
        request.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
        return request
    }

    def 'interleaved response'() {
        given:
        def eventLoopGroup = new NioEventLoopGroup(1)
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NotNull Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
                                        responses.add(msg)
                                    }
                                })
                    }
                })
                .remoteAddress(embeddedServer.host, embeddedServer.port)

        when:
        def channel = bootstrap.connect().sync().channel()
        // send two requests in one tcp packet
        channel.write(makeBlockRequest('foo'))
        channel.write(makeBlockRequest('bar'))
        channel.flush()

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 2
        }
        responses[0].content().toString(StandardCharsets.UTF_8) == '[foo1,foo2]'
        responses[1].content().toString(StandardCharsets.UTF_8) == '[bar1,bar2]'

        cleanup:
        responses*.content().forEach(ByteBuf::release)
        eventLoopGroup.shutdownGracefully()
    }

    def 'simple get requests'() {
        given:
        def eventLoopGroup = new NioEventLoopGroup(1)
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NotNull Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
                                        responses.add(msg)
                                    }
                                })
                    }
                })
                .remoteAddress(embeddedServer.host, embeddedServer.port)

        when:
        def channel = bootstrap.connect().sync().channel()
        // send many requests in one tcp packet
        for (int i = 0; i < 10; i++) {
            channel.write(makeSimpleRequest())
        }
        channel.flush()

        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 10
        }
        for (def r : responses) {
            def content = r.content()
            assert content.toString(StandardCharsets.UTF_8) == 'foo'
            content.release()
        }

        cleanup:
        eventLoopGroup.shutdownGracefully()
    }

    @Singleton
    @Controller
    @Requires(property = 'spec.name', value = 'HttpPipeliningSpec')
    static class BlockingController {
        @Post('/block')
        Publisher<String> block(@Body String msg) {
            return Flux.concat(
                    Mono.just(msg + '1'),
                    Mono.just(msg + '2').delayElement(Duration.ofSeconds(1))
            )
        }

        @Get('/plain')
        String plain() {
            println 'plain'
            return 'foo'
        }
    }
}
