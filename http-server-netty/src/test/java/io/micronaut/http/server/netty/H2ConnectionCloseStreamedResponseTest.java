package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
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
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.util.ReferenceCountUtil;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * An HTTP/2 TCP close must cancel an open streamed response.
 */
class H2ConnectionCloseStreamedResponseTest {
    private static final String SPEC_NAME = "H2ConnectionCloseStreamedResponseTest";

    @Test
    void tcpCloseClosesOpenSseEmitters() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.http-version", "2.0",
            "micronaut.server.ssl.enabled", false))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            Recorder recorder = ctx.getBean(Recorder.class);
            try (EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())) {
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
                        headers.method("GET").scheme("http").authority("localhost").path("/h2-close/sse");
                        channel.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true).stream(stream));
                    });
                    firstData.get(10, TimeUnit.SECONDS);
                    // plain TCP close, no RST_STREAM / GOAWAY
                    // bypass the pipeline so Netty does not send a GOAWAY
                    channel.eventLoop().submit(() -> channel.unsafe().closeForcibly()).sync();
                }
                Thread.sleep(1500);
                assertEquals(3, recorder.closed.size(), "streamed responses cancelled after the HTTP/2 connection closed");
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder {
        final List<Object> closed = new CopyOnWriteArrayList<>();
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Ctrl {
        private final Recorder recorder;

        Ctrl(Recorder recorder) {
            this.recorder = recorder;
        }

        @Get(value = "/h2-close/sse", produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<String> sse() {
            return Flux.concat(Flux.just("first"), Flux.<String>never()).doOnCancel(() -> recorder.closed.add("cancel"));
        }
    }
}
