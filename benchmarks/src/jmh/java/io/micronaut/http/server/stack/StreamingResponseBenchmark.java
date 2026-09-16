package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for streaming a {@link Flux} of many small records out of the server, both as a JSON
 * array and as a JSON stream.
 */
public class StreamingResponseBenchmark {
    private static final int COUNT = 200;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(StreamingResponseBenchmark.class.getName() + ".*")
            .warmupIterations(5)
            .measurementIterations(5)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.MICROSECONDS)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    @Benchmark
    public void test(Holder holder) {
        holder.exchangeAndVerify();
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param
        Request request;

        ApplicationContext ctx;
        EmbeddedChannel channel;
        ByteBuf requestBytes;
        ByteBuf responseBytes;

        @Setup
        public void setUp(Blackhole blackhole) {
            ctx = ApplicationContext.run(Map.of(
                "spec.name", "StreamingResponseBenchmark",
                "micronaut.server.date-header", false // disabling this makes the response identical each time
            ));
            ctx.registerSingleton(Blackhole.class, blackhole);
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            channel = ((NettyHttpServer) server).buildEmbeddedChannel(false);

            EmbeddedChannel clientChannel = new EmbeddedChannel();
            clientChannel.pipeline().addLast(new HttpClientCodec());
            clientChannel.pipeline().addLast(new HttpObjectAggregator(1024 * 1024));

            clientChannel.writeOutbound(request.request());
            clientChannel.flushOutbound();

            requestBytes = NettyUtil.readAllOutboundContiguous(clientChannel);

            // sanity check: run req/resp once and see that the response is correct
            responseBytes = exchange();
            clientChannel.writeInbound(responseBytes.retainedDuplicate());
            FullHttpResponse response = clientChannel.readInbound();
            request.verifyResponse(response);
            response.release();
        }

        ByteBuf exchange() {
            channel.writeInbound(requestBytes.retainedDuplicate());
            channel.runPendingTasks();
            return NettyUtil.readAllOutboundComposite(channel);
        }

        /**
         * Benchmark body: send the request and compare each outbound part against the expected
         * response as it arrives, without building a composite (composite comparison is far more
         * expensive than the server work being measured, and its cost varies with how the response
         * happens to be fragmented, which is exactly what this benchmark changes).
         */
        void exchangeAndVerify() {
            channel.writeInbound(requestBytes.retainedDuplicate());
            int offset = 0;
            int expectedLength = responseBytes.readableBytes();
            while (true) {
                ByteBuf part = channel.readOutbound();
                if (part == null) {
                    channel.runPendingTasks();
                    part = channel.readOutbound();
                    if (part == null) {
                        break;
                    }
                }
                int n = part.readableBytes();
                boolean ok = offset + n <= expectedLength
                    && ByteBufUtil.equals(responseBytes, responseBytes.readerIndex() + offset, part, part.readerIndex(), n);
                part.release();
                if (!ok) {
                    throw new AssertionError("Response did not match at offset " + offset);
                }
                offset += n;
            }
            if (offset != expectedLength) {
                throw new AssertionError("Response length " + offset + " != " + expectedLength);
            }
        }

        @TearDown
        public void tearDown() {
            ctx.close();
            requestBytes.release();
            responseBytes.release();
        }
    }

    public enum Request {
        /**
         * A {@link Flux} of {@value COUNT} records rendered as a single JSON array.
         */
        JSON_STREAM_RESPONSE {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/streaming-benchmark/records");
                request.headers().add(HttpHeaderNames.ACCEPT, MediaType.APPLICATION_JSON);
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                String body = response.content().toString(StandardCharsets.UTF_8);
                Assertions.assertTrue(body.startsWith("[{\"id\":0,"), body.substring(0, Math.min(64, body.length())));
                Assertions.assertTrue(body.endsWith("}]"), body.substring(Math.max(0, body.length() - 64)));
                assertAllRecords(body);
            }
        },
        /**
         * A {@link Flux} of {@value COUNT} records rendered as a JSON stream.
         */
        JSON_STREAM_RESPONSE_NDJSON {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/streaming-benchmark/records-stream");
                request.headers().add(HttpHeaderNames.ACCEPT, MediaType.APPLICATION_JSON_STREAM);
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                String body = response.content().toString(StandardCharsets.UTF_8);
                Assertions.assertTrue(body.startsWith("{\"id\":0,"), body.substring(0, Math.min(64, body.length())));
                assertAllRecords(body);
            }
        };

        abstract FullHttpRequest request();

        abstract void verifyResponse(FullHttpResponse response);

        /**
         * A truncated stream would otherwise pass the prefix checks and then be measured as if it
         * were complete.
         */
        static void assertAllRecords(String body) {
            for (int i = 0; i < COUNT; i++) {
                String id = "{\"id\":" + i + ",";
                Assertions.assertTrue(body.contains(id), "record " + i + " missing from the response");
            }
        }
    }

    /**
     * A small record, as would be returned from a paged database query.
     *
     * @param id      The id
     * @param message The message
     * @param value   The value
     */
    public record Rec(int id, String message, long value) {
    }

    @Singleton
    @Controller("/streaming-benchmark")
    @Requires(property = "spec.name", value = "StreamingResponseBenchmark")
    public static class StreamController {
        private final List<Rec> records;

        StreamController() {
            List<Rec> list = new ArrayList<>(COUNT);
            for (int i = 0; i < COUNT; i++) {
                list.add(new Rec(i, "message-" + i, i * 31L));
            }
            this.records = List.copyOf(list);
        }

        @Get("/records")
        @Produces(MediaType.APPLICATION_JSON)
        public Flux<Rec> records() {
            return Flux.fromIterable(records);
        }

        @Get("/records-stream")
        @Produces(MediaType.APPLICATION_JSON_STREAM)
        public Flux<Rec> recordsStream() {
            return Flux.fromIterable(records);
        }
    }
}
