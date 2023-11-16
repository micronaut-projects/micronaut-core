package io.micronaut.http.server.netty.jackson;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.netty.body.JsonCounter;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.body.ByteBody;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.json.convert.LazyJsonNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class JsonContentProcessorBenchmark {
    private static final Argument<Payload> PAYLOAD_ARGUMENT = Argument.of(Payload.class);

    public static void main(String[] args) throws Throwable {
        if (false) {
            Input input = new Input();
            input.size = "6-100000";
            input.chunkSize = 1024;
            input.direct = false;
            input.setUp();
            new JsonContentProcessorBenchmark().benchmarkNew(input);
            //new JsonContentProcessorBenchmark().benchmarkNew(input);
            return;
        }

        new Runner(new OptionsBuilder()
            .include(JsonContentProcessorBenchmark.class.getSimpleName())
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(1)
            .warmupIterations(3)
            .measurementIterations(5)
            //.addProfiler(LinuxPerfAsmProfiler.class)
            //.addProfiler(AsyncProfiler.class, "libPath=/home/yawkat/bin/async-profiler-2.9-linux-x64/build/libasyncProfiler.so;output=flamegraph")
            .build()).run();
    }

    @State(Scope.Benchmark)
    public static class Input {
        @Param({
            //"6",
            //"1000",
            //"1000000",
            //"100000-6",
            "6-100000",
        })
        public String size;

        @Param({
            //"256",
            "1024",
           // "1000000",
        })
        public int chunkSize;

        @Param({
            //"true",
            "false"
        })
        public boolean direct;

        byte[] bytes;
        List<ByteBuf> bufs;

        JsonMapper jsonMapper;
        NettyHttpRequest<?> request;
        NettyHttpServerConfiguration configuration;

        @Setup
        public void setUp() throws IOException {
            bufs = new ArrayList<>();
            bytes = Files.readAllBytes(Paths.get("/home/yawkat/dev/mn/micronaut-benchmark/test-body-" + size + ".json"));
            for (int off = 0; off < bytes.length; off += chunkSize) {
                ByteBuf buf = direct ? PooledByteBufAllocator.DEFAULT.directBuffer(chunkSize) : PooledByteBufAllocator.DEFAULT.heapBuffer(chunkSize);
                buf.writeBytes(bytes, off, Math.min(chunkSize, bytes.length - off));
                bufs.add(buf);
            }

            jsonMapper = ApplicationContext.run().getBean(JsonMapper.class);
            configuration = new NettyHttpServerConfiguration();
            EmbeddedChannel ch = new EmbeddedChannel();
            ch.pipeline().addLast(new ChannelHandlerAdapter() {
            });
            request = new NettyHttpRequest<>(
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"),
                ByteBody.empty(),
                ch.pipeline().firstContext(),
                ConversionService.SHARED,
                configuration
            );
        }

        @TearDown
        public void tearDown() {
            for (ByteBuf buf : bufs) {
                buf.release();
            }
        }
    }

    @Benchmark
    public Payload benchmarkNew(Input input) throws Throwable {
        var processor = new JsonContentProcessor(input.request, input.configuration, input.jsonMapper);
        List<Object> out = new ArrayList<>();
        for (ByteBuf buf : input.bufs) {
            processor.onData(new DefaultByteBufHolder(buf.retainedDuplicate()), out);
        }
        processor.complete(out);
        if (out.size() != 1) {
            throw new AssertionError();
        }
        LazyJsonNode lazy = (LazyJsonNode) out.get(0);
        try {
            return lazy.parse(input.jsonMapper, PAYLOAD_ARGUMENT);
        } finally {
            lazy.release();
        }
    }

    /* JsonContentProcessorOld isn't committed so this doesn't compile
    @Benchmark
    public Payload benchmarkOld(Input input) throws Throwable {
        var processor = new JsonContentProcessorOld(input.request, input.configuration, input.jsonMapper);
        List<Object> out = new ArrayList<>();
        for (ByteBuf buf : input.bufs) {
            processor.onData(new DefaultByteBufHolder(buf.retain()), out);
        }
        processor.complete(out);
        if (out.size() != 1) {
            throw new AssertionError();
        }
        JsonNode node = (JsonNode) out.get(0);
        return input.jsonMapper.readValueFromTree(node, PAYLOAD_ARGUMENT);
    }
     */

    //@Benchmark
    public JsonCounter.BufferRegion count(Input input) throws JsonSyntaxException {
        JsonCounter counter2 = new JsonCounter();
        for (ByteBuf buf : input.bufs) {
            counter2.feed(buf.duplicate());
        }
        return counter2.pollFlushedRegion();
    }

    @Introspected
    record Payload(List<String> haystack, String needle) {}
}
