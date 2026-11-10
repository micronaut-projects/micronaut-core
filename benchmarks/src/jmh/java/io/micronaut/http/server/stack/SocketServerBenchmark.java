package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Requests to a Micronaut server over real loopback sockets, served by its real event loops. The
 * embedded channel benchmarks run the whole stack on the calling thread, so they cannot see the
 * cost of moving a response between threads or of HTTP/2 stream handling on the event loop: this
 * one can. Each benchmark thread holds one connection and sends one request at a time on it.
 *
 * <p>The client is a bare Netty client, so that the measured time is mostly the server's.
 * {@code Throughput} gives requests per second, {@code SampleTime} the latency percentiles; run
 * with {@code -t <n>} for more concurrent connections and {@code -prof gc} for the allocations.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Threads(4)
public class SocketServerBenchmark {

    static final String SPEC_NAME = "SocketServerBenchmark";
    private static final int BODY_LENGTH = 512;
    private static final String BODY = "x".repeat(BODY_LENGTH);
    // the stream endpoint answers four pieces
    private static final String PIECE = "y".repeat(BODY_LENGTH / 4);

    /**
     * The protocol of the connections.
     */
    @Param({"HTTP_1_1", "H2C"})
    Protocol protocol;

    /**
     * Where the response comes from.
     */
    @Param({"LOOP", "BLOCKING", "STREAM"})
    Endpoint endpoint;

    ApplicationContext ctx;
    EventLoopGroup clientGroup;
    int port;

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(SocketServerBenchmark.class.getName() + ".*")
            .warmupIterations(5)
            .measurementIterations(5)
            .forks(1)
            .build();
        new Runner(opt).run();
    }

    @Setup
    public void setUp(BenchmarkParams params) {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.http-version", protocol == Protocol.H2C ? "2.0" : "1.1"
        ));
        port = ctx.getBean(EmbeddedServer.class).start().getPort();
        clientGroup = new MultiThreadIoEventLoopGroup(Math.max(1, params.getThreads() / 2), NioIoHandler.newFactory());
    }

    @TearDown
    public void tearDown() throws Exception {
        clientGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        ctx.close();
    }

    @Benchmark
    public int request(ClientConnection connection) throws Exception {
        return connection.send(endpoint.path);
    }

    /**
     * One connection per benchmark thread.
     */
    @State(Scope.Thread)
    public static class ClientConnection {
        private Channel channel;
        private Protocol protocol;
        private Http2StreamChannelBootstrap streams;
        private volatile CompletableFuture<Integer> pending;

        @Setup(Level.Trial)
        public void connect(SocketServerBenchmark benchmark) throws Exception {
            protocol = benchmark.protocol;
            channel = new Bootstrap()
                .group(benchmark.clientGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        if (protocol == Protocol.H2C) {
                            ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), new Http2MultiplexHandler(new SimpleChannelInboundHandler<>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                    // no streams are pushed
                                }
                            }));
                        } else {
                            ch.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(1 << 20), new ResponseHandler());
                        }
                    }
                })
                .connect("127.0.0.1", benchmark.port)
                .sync()
                .channel();
            if (protocol == Protocol.H2C) {
                streams = new Http2StreamChannelBootstrap(channel).handler(new ChannelInitializer<Http2StreamChannel>() {
                    @Override
                    protected void initChannel(Http2StreamChannel ch) {
                        ch.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(false), new HttpObjectAggregator(1 << 20), new ResponseHandler());
                    }
                });
            }
        }

        @TearDown(Level.Trial)
        public void close() throws Exception {
            channel.close().sync();
        }

        /**
         * Send one request and wait for its complete response.
         *
         * @param path The path
         * @return The length of the response body
         */
        int send(String path) throws Exception {
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
            request.headers().set(HttpHeaderNames.HOST, "localhost");
            CompletableFuture<Integer> response = new CompletableFuture<>();
            pending = response;
            Channel target = channel;
            if (protocol == Protocol.H2C) {
                request.headers().set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTP.name());
                target = streams.open().sync().getNow();
            }
            try {
                target.writeAndFlush(request);
                return response.get(10, TimeUnit.SECONDS);
            } finally {
                if (target != channel) {
                    target.close();
                }
            }
        }

        /**
         * Checks a complete response and releases it where it arrives, so that a response that
         * comes after the wait gave up is released too.
         */
        private final class ResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
                // every endpoint answers BODY_LENGTH bytes: a shorter body is a dropped piece,
                // which must fail the run rather than look faster
                int length = msg.content().readableBytes();
                if (msg.status().code() != 200 || length != BODY_LENGTH) {
                    pending.completeExceptionally(new IllegalStateException("Unexpected response " + msg.status() + " " + ByteBufUtil.hexDump(msg.content())));
                } else {
                    pending.complete(length);
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                pending.completeExceptionally(cause);
            }
        }
    }

    /**
     * The protocol of a connection.
     */
    public enum Protocol {
        HTTP_1_1,
        /**
         * HTTP/2 over cleartext with prior knowledge.
         */
        H2C
    }

    /**
     * Where the response comes from.
     */
    public enum Endpoint {
        /**
         * Answered on the event loop.
         */
        LOOP("/socket-bench/loop"),
        /**
         * Answered on the blocking executor: a full response written from another thread.
         */
        BLOCKING("/socket-bench/blocking"),
        /**
         * A streamed response set up on the blocking executor: its body is claimed off the
         * event loop.
         */
        STREAM("/socket-bench/stream");

        final String path;

        Endpoint(String path) {
            this.path = path;
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/socket-bench")
    public static class SocketBenchController {
        @Get(value = "/loop", produces = "text/plain")
        public String loop() {
            return BODY;
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get(value = "/blocking", produces = "text/plain")
        public String blocking() {
            return BODY;
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get(value = "/stream", produces = "text/plain")
        public Publisher<String> stream() {
            return Flux.just(PIECE, PIECE, PIECE, PIECE);
        }
    }
}
