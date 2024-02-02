package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.junit.jupiter.api.Assertions;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FullHttpStackBenchmark {
    @Benchmark
    public void test(Holder holder) {
        ByteBuf response = holder.exchange();
        if (!holder.responseBytes.equals(response)) {
            throw new AssertionError("Response did not match");
        }
        response.release();
    }

    public static void main(String[] args) throws Exception {
        JmhFastThreadLocalExecutor exec = new JmhFastThreadLocalExecutor(1, "init-test");
        exec.submit(() -> {
            // simple test that everything works properly
            for (StackFactory stack : StackFactory.values()) {
                Holder holder = new Holder();
                holder.stack = stack;
                holder.setUp();
                holder.tearDown();
            }
            return null;
        }).get();
        exec.shutdown();

        Options opt = new OptionsBuilder()
            .include(FullHttpStackBenchmark.class.getName() + ".*")
            .warmupIterations(20)
            .measurementIterations(30)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(1)
            .jvmArgsAppend("-Djmh.executor=CUSTOM", "-Djmh.executor.class=" + JmhFastThreadLocalExecutor.class.getName())
            .build();

        new Runner(opt).run();
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({"MICRONAUT"/*, "PURE_NETTY"*/})
        StackFactory stack = StackFactory.MICRONAUT;

        AutoCloseable ctx;
        EmbeddedChannel channel;
        ByteBuf requestBytes;
        ByteBuf responseBytes;

        @Setup
        public void setUp() {
            if (!(Thread.currentThread() instanceof FastThreadLocalThread)) {
                throw new IllegalStateException("Should run on a netty FTL thread");
            }

            Stack stack = this.stack.openChannel();
            ctx = stack.closeable;
            channel = stack.serverChannel;

            channel.freezeTime();

            EmbeddedChannel clientChannel = new EmbeddedChannel();
            clientChannel.pipeline().addLast(new HttpClientCodec());
            clientChannel.pipeline().addLast(new HttpObjectAggregator(1000));

            FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/search/find",
                Unpooled.wrappedBuffer("{\"haystack\": [\"xniomb\", \"seelzp\", \"nzogdq\", \"omblsg\", \"idgtlm\", \"ydonzo\"], \"needle\": \"idg\"}".getBytes(StandardCharsets.UTF_8))
            );
            request.headers().add(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
            request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            request.headers().add(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON);
            clientChannel.writeOutbound(request);
            clientChannel.flushOutbound();

            requestBytes = PooledByteBufAllocator.DEFAULT.buffer();
            while (true) {
                ByteBuf part = clientChannel.readOutbound();
                if (part == null) {
                    break;
                }
                requestBytes.writeBytes(part);
            }

            // sanity check: run req/resp once and see that the response is correct
            responseBytes = exchange();
            clientChannel.writeInbound(responseBytes.retainedDuplicate());
            FullHttpResponse response = clientChannel.readInbound();
            //System.out.println(response);
            //System.out.println(response.content().toString(StandardCharsets.UTF_8));
            Assertions.assertEquals(HttpResponseStatus.OK, response.status());
            Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            String expectedResponseBody = "{\"listIndex\":4,\"stringIndex\":0}";
            Assertions.assertEquals(expectedResponseBody, response.content().toString(StandardCharsets.UTF_8));
            Assertions.assertEquals(expectedResponseBody.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
            response.release();
        }

        private ByteBuf exchange() {
            channel.writeInbound(requestBytes.retainedDuplicate());
            channel.runPendingTasks();
            CompositeByteBuf response = PooledByteBufAllocator.DEFAULT.compositeBuffer();
            while (true) {
                ByteBuf part = channel.readOutbound();
                if (part == null) {
                    break;
                }
                response.addComponent(true, part);
            }
            return response;
        }

        @TearDown
        public void tearDown() throws Exception {
            ctx.close();
            requestBytes.release();
            responseBytes.release();
        }
    }

    public enum StackFactory {
        MICRONAUT {
            @Override
            Stack openChannel() {
                ApplicationContext ctx = ApplicationContext.run(Map.of(
                    "spec.name", "FullHttpStackBenchmark",
                    //"micronaut.server.netty.server-type", NettyHttpServerConfiguration.HttpServerType.FULL_CONTENT,
                    "micronaut.server.date-header", false, // disabling this makes the response identical each time
                    "micronaut.server.netty.optimized-routing", true
                ));
                EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
                EmbeddedChannel channel = ((NettyHttpServer) server).buildEmbeddedChannel(false);
                return new Stack(channel, ctx);
            }
        },
        PURE_NETTY {
            @Override
            Stack openChannel() {
                HttpObjectAggregator aggregator = new HttpObjectAggregator(10_000_000);
                aggregator.setMaxCumulationBufferComponents(100000);
                EmbeddedChannel channel = new EmbeddedChannel();
                channel.pipeline().addLast(new HttpServerCodec());
                channel.pipeline().addLast(aggregator);
                channel.pipeline().addLast(new RequestHandler());
                return new Stack(channel, () -> {
                });
            }
        };

        abstract Stack openChannel();
    }

    private record Stack(EmbeddedChannel serverChannel, AutoCloseable closeable) {
    }

}
