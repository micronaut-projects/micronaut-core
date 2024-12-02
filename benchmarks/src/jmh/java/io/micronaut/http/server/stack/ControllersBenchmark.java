package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
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
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.AsyncProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark that mimics the TechEmpower framework benchmarks.
 */
public class ControllersBenchmark {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(ControllersBenchmark.class.getName() + ".*")
            .warmupIterations(20)
            .measurementIterations(30)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    @Benchmark
    public void test(Holder holder) {
        ByteBuf response = holder.exchange();
        if (!holder.responseBytes.equals(response)) {
            throw new AssertionError("Response did not match");
        }
        response.release();
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
                "spec.name", "ControllersBenchmark",
                "micronaut.server.date-header", false // disabling this makes the response identical each time
            ));
            ctx.registerSingleton(Blackhole.class, blackhole);
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            channel = ((NettyHttpServer) server).buildEmbeddedChannel(false);

            EmbeddedChannel clientChannel = new EmbeddedChannel();
            clientChannel.pipeline().addLast(new HttpClientCodec());
            clientChannel.pipeline().addLast(new HttpObjectAggregator(1000));

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

        @TearDown
        public void tearDown() {
            ctx.close();
            requestBytes.release();
            responseBytes.release();
        }
    }

    public enum Request {
        TFB_LIKE {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/tfblike");
                request.headers().add(HttpHeaderNames.ACCEPT, "text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("text/plain", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                String expectedResponseBody = "Hello, World!";
                Assertions.assertEquals(expectedResponseBody, response.content().toString(StandardCharsets.UTF_8));
                Assertions.assertEquals(expectedResponseBody.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
            }
        },
        MISSING_QUERY_PARAMETER {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ctrl/text-echo/foo");
                request.headers().add(HttpHeaderNames.ACCEPT, "text/plain");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("text/plain", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                String expectedResponseBody = "foo";
                Assertions.assertEquals(expectedResponseBody, response.content().toString(StandardCharsets.UTF_8));
                Assertions.assertEquals(expectedResponseBody.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
            }
        }
        ;

        abstract FullHttpRequest request();

        abstract void verifyResponse(FullHttpResponse response);
    }

    @Controller("/tfblike")
    @Requires(property = "spec.name", value = "ControllersBenchmark")
    static class TfbLikeController {

        private static final byte[] TEXT = "Hello, World!".getBytes(StandardCharsets.UTF_8);

        @Get(value = "/", produces = MediaType.TEXT_PLAIN)
        public byte[] getPlainText() {
            return TEXT;
        }
    }

    @Controller("/ctrl")
    @Requires(property = "spec.name", value = "ControllersBenchmark")
    static class MyController {
        @Inject
        Blackhole blackhole;

        @Get(uri = "/text-echo/{text}")
        @Produces(MediaType.TEXT_PLAIN)
        String echoMissingParameter(String text,
                                    @Nullable @QueryValue("firstParameter") Integer firstParameter,
                                    @Nullable @QueryValue("secondParameter") Integer secondParameter) {
            blackhole.consume(firstParameter);
            blackhole.consume(secondParameter);
            return text;
        }
    }
}
