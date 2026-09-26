package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.exceptions.StreamResetException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An HTTP/2 server that resets the stream of every request: a refused stream was not processed,
 * so it is an {@link UnprocessedRequestException}; any other reset is a {@link StreamResetException}.
 */
class Http2StreamResetTest {

    @Test
    void refusedStreamIsUnprocessed() throws Exception {
        try (ResettingServer server = new ResettingServer(Http2Error.REFUSED_STREAM);
             ApplicationContext ctx = start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            UnprocessedRequestException failure = Assertions.assertThrows(UnprocessedRequestException.class, () -> exchange(client, server));
            Assertions.assertEquals(UnprocessedRequestException.Reason.STREAM_REFUSED, failure.getReason());
            Assertions.assertEquals(server.uri(), failure.getUri().orElseThrow());
        }
    }

    @Test
    void otherResetIsAStreamReset() throws Exception {
        try (ResettingServer server = new ResettingServer(Http2Error.CANCEL);
             ApplicationContext ctx = start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            StreamResetException failure = Assertions.assertThrows(StreamResetException.class, () -> exchange(client, server));
            Assertions.assertEquals(Http2Error.CANCEL.code(), failure.getErrorCode());
            Assertions.assertFalse(UnprocessedRequestException.isUnprocessed(failure));
        }
    }

    private static ApplicationContext start() {
        return ApplicationContext.run(Map.of(
            "micronaut.http.client.plaintext-mode", "h2c_prior_knowledge"
        ));
    }

    private static void exchange(RawHttpClient client, ResettingServer server) {
        HttpResponse<?> response = Mono.from(client.exchange(HttpRequest.GET(server.uri()), null, null)).block();
        if (response instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
            byteBodyResponse.close();
        }
    }

    /**
     * A plaintext HTTP/2 server that answers every request with {@code RST_STREAM}.
     */
    private static final class ResettingServer implements AutoCloseable {
        private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        private final Channel channel;

        ResettingServer(Http2Error error) throws InterruptedException {
            channel = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(
                            Http2FrameCodecBuilder.forServer().build(),
                            new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                                @Override
                                protected void initChannel(Channel stream) {
                                    stream.pipeline().addLast(new SimpleChannelInboundHandler<Http2HeadersFrame>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, Http2HeadersFrame headers) {
                                            ctx.writeAndFlush(new DefaultHttp2ResetFrame(error));
                                        }
                                    });
                                }
                            })
                        );
                    }
                })
                .bind(InetAddress.getLoopbackAddress(), 0)
                .sync()
                .channel();
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + ((InetSocketAddress) channel.localAddress()).getPort() + "/reset");
        }

        @Override
        public void close() {
            channel.close();
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
    }
}
