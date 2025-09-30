package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import org.junit.jupiter.api.Assertions;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RawClientBenchmark {
    public static void main(String[] args) throws Exception {
        JmhFastThreadLocalExecutor exec = new JmhFastThreadLocalExecutor(1, "init-test");
        exec.submit(() -> {
            // simple test that everything works properly
            for (FullHttpStackBenchmark.StackFactory stack : FullHttpStackBenchmark.StackFactory.values()) {
                FullHttpStackBenchmark.Holder holder = new FullHttpStackBenchmark.Holder();
                holder.stack = stack;
                holder.setUp();
                holder.tearDown();
            }
            return null;
        }).get();
        exec.shutdown();

        Options opt = new OptionsBuilder()
            .include(RawClientBenchmark.class.getName() + ".*")
            .warmupIterations(5)
            .measurementIterations(10)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    @Benchmark
    public byte @NonNull [] benchmark(Holder holder) throws Exception {
        try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(
            holder.client.exchange(holder.request, holder.requestBody.split(), null)).block()) {
            assert response != null;
            return response.byteBody().buffer().get().toByteArray();
        }
    }

    @State(Scope.Thread)
    public static class Holder {
        ApplicationContext ctx;
        RawHttpClient client;

        HttpRequest<?> request;
        CloseableAvailableByteBody requestBody;

        EventLoopGroup serverLoop;

        @Setup
        public void setUp() throws Exception {
            ctx = ApplicationContext.run(Map.of("spec.name", "RawClientBenchmark"));
            client = ctx.getBean(RawHttpClient.class);

            serverLoop = new NioEventLoopGroup(1);
            ServerSocketChannel server = (ServerSocketChannel) new ServerBootstrap()
                .group(serverLoop)
                .channel(NioServerSocketChannel.class)
                .localAddress(0)
                .childHandler(new ChannelInitializer<>() {
                    FullHttpResponse response;

                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) {
                        ByteBuf resp = ctx.alloc().buffer();
                        ByteBufUtil.writeAscii(resp, "bar");
                        response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, resp, new DefaultHttpHeaders().add(HttpHeaderNames.CONTENT_LENGTH, resp.readableBytes()), EmptyHttpHeaders.INSTANCE);
                    }

                    @Override
                    protected void initChannel(@NonNull Channel ch) {
                        ch.pipeline()
                            .addLast(new HttpServerCodec())
                            .addLast(new ChannelInboundHandlerAdapter() {
                                boolean inBody = false;

                                @Override
                                public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                                    if (!inBody) {
                                        inBody = true;
                                        if (!(msg instanceof FullHttpResponse)) {
                                            return;
                                        }
                                    }
                                    ((HttpContent) msg).release();
                                    if (msg instanceof LastHttpContent) {
                                        ctx.writeAndFlush(new DefaultFullHttpResponse(
                                            response.protocolVersion(),
                                            response.status(),
                                            response.content().retainedSlice(),
                                            response.headers(),
                                            response.trailingHeaders()
                                        ));
                                        inBody = false;
                                    }
                                }
                            });
                    }
                })
                .bind().syncUninterruptibly().channel();

            request = HttpRequest.POST("http://127.0.0.1:" + server.localAddress().getPort() + "/foo", null);
            requestBody = new NettyByteBodyFactory(server).copyOf("foo", StandardCharsets.UTF_8);

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(client.exchange(request, requestBody.split(), null)).block()) {
                Assertions.assertEquals("bar", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }

        @TearDown
        public void tearDown() {
            requestBody.close();
            ctx.close();
            serverLoop.shutdownGracefully();
        }
    }
}
