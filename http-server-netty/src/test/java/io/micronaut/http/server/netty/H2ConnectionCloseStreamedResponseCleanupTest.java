package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Body;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.micronaut.http.MediaType;
import reactor.core.publisher.Flux;
import org.reactivestreams.Publisher;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.util.ReferenceCountUtil;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An HTTP/2 TCP close after the request has ended must clean up the request of a response that
 * is still streaming.
 */
class H2ConnectionCloseStreamedResponseCleanupTest {
    private static final String SPEC_NAME = "H2ConnectionCloseStreamedResponseCleanupTest";

    @ParameterizedTest
    @ValueSource(strings = {"/h2-cleanup/sse", "/h2-cleanup/sse-post"})
    void tcpCloseCleansUpTheRequest(String path) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.http-version", "2.0",
            "micronaut.server.ssl.enabled", false))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            Recorder recorder = ctx.getBean(Recorder.class);
            EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            try {
                for (int i = 0; i < 3; i++) {
                    CompletableFuture<Void> firstData = new CompletableFuture<>();
                    Channel channel = new Bootstrap()
                        .group(group)
                        .channel(NioSocketChannel.class)
                        .remoteAddress(server.getHost(), server.getPort())
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), new Http2ChannelDuplexHandler() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext c, Object msg) {
                                        if (msg instanceof Http2DataFrame) {
                                            firstData.complete(null);
                                        }
                                        ReferenceCountUtil.release(msg);
                                    }
                                });
                            }
                        })
                        .connect().sync().channel();
                    channel.eventLoop().execute(() -> {
                        Http2FrameStream stream = ((Http2ChannelDuplexHandler) channel.pipeline().last()).newStream();
                        DefaultHttp2Headers headers = new DefaultHttp2Headers();
                        boolean post = path.endsWith("-post");
                        headers.method(post ? "POST" : "GET").scheme("http").authority("localhost").path(path);
                        channel.write(new DefaultHttp2HeadersFrame(headers, !post).stream(stream));
                        if (post) {
                            channel.write(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("body", StandardCharsets.UTF_8), true).stream(stream));
                        }
                        channel.flush();
                    });
                    firstData.get(10, TimeUnit.SECONDS);
                    // plain TCP close, no RST_STREAM / GOAWAY
                    // bypass the pipeline so Netty does not send a GOAWAY
                    channel.eventLoop().submit(() -> channel.unsafe().closeForcibly()).sync();
                }
                Thread.sleep(1500);
                long deadline = System.currentTimeMillis() + 5000;
                while (recorder.terminated.size() < 3 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50);
                }
                assertEquals(3, recorder.terminated.size(), "requests cleaned up after the HTTP/2 connection closed");
            } finally {
                group.shutdownGracefully();
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        final List<Object> terminated = new CopyOnWriteArrayList<>();

        @Override
        public void onApplicationEvent(HttpRequestTerminatedEvent event) {
            terminated.add(event.getSource().getPath());
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Ctrl {
        @Get(value = "/h2-cleanup/sse", produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<String> sse() {
            return Flux.concat(Flux.just("first"), Flux.<String>never());
        }

        @Post(value = "/h2-cleanup/sse-post", produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<String> ssePost(@Body String body) {
            return Flux.concat(Flux.just(body), Flux.<String>never());
        }
    }
}
