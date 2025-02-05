package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
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
import org.jetbrains.annotations.NotNull;
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
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/tfblike/bytes");
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
        TFB_STRING {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/tfblike/string");
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
        TFB_LIKE_BEANS1 {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/tfblike/beans1");
                request.headers().add(HttpHeaderNames.ACCEPT, "application/json");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                String expectedResponseBody = """
[{"id":1,"message":"A"},{"id":2,"message":"B"},{"id":3,"message":"C"}]""";
                Assertions.assertEquals(expectedResponseBody, response.content().toString(StandardCharsets.UTF_8));
                Assertions.assertEquals(expectedResponseBody.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
            }
        },
        TFB_LIKE_BEANS2 {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/tfblike/beans2");
                request.headers().add(HttpHeaderNames.ACCEPT, "application/json");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                String expectedResponseBody = """
[{"id":1,"randomNumber":123},{"id":2,"randomNumber":456},{"id":3,"randomNumber":789}]""";
                Assertions.assertEquals(expectedResponseBody, response.content().toString(StandardCharsets.UTF_8));
                Assertions.assertEquals(expectedResponseBody.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
            }
        },
        TFB_LIKE_ASYNC_BEANS1 {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/tfblike/async/beans1");
                request.headers().add(HttpHeaderNames.ACCEPT, "application/json");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                String expectedResponseBody = """
[{"id":1,"message":"A"},{"id":2,"message":"B"},{"id":3,"message":"C"}]""";
                Assertions.assertEquals(expectedResponseBody, response.content().toString(StandardCharsets.UTF_8));
                Assertions.assertEquals(expectedResponseBody.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
            }
        },
        TFB_LIKE_ASYNC_BEANS2 {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/tfblike/async/beans2");
                request.headers().add(HttpHeaderNames.ACCEPT, "application/json");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                String expectedResponseBody = """
[{"id":1,"randomNumber":123},{"id":2,"randomNumber":456},{"id":3,"randomNumber":789}]""";
                Assertions.assertEquals(expectedResponseBody, response.content().toString(StandardCharsets.UTF_8));
                Assertions.assertEquals(expectedResponseBody.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
            }
        },
// Type pollution because of the Reactor
//        TFB_LIKE_REACTIVE_BEANS1 {
//            @Override
//            FullHttpRequest request() {
//                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/tfblike/reactive/beans1");
//                request.headers().add(HttpHeaderNames.ACCEPT, "application/json");
//                return request;
//            }
//
//            @Override
//            void verifyResponse(FullHttpResponse response) {
//                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
//                Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
//                String expectedResponseBody = """
//[{"id":1,"message":"A"},{"id":2,"message":"B"},{"id":3,"message":"C"}]""";
//                Assertions.assertEquals(expectedResponseBody, response.content().toString(StandardCharsets.UTF_8));
//                Assertions.assertEquals(expectedResponseBody.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
//            }
//        },
//        TFB_LIKE_REACTIVE_BEANS2 {
//            @Override
//            FullHttpRequest request() {
//                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/tfblike/reactive/beans2");
//                request.headers().add(HttpHeaderNames.ACCEPT, "application/json");
//                return request;
//            }
//
//            @Override
//            void verifyResponse(FullHttpResponse response) {
//                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
//                Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
//                String expectedResponseBody = """
//[{"id":1,"randomNumber":123},{"id":2,"randomNumber":456},{"id":3,"randomNumber":789}]""";
//                Assertions.assertEquals(expectedResponseBody, response.content().toString(StandardCharsets.UTF_8));
//                Assertions.assertEquals(expectedResponseBody.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
//            }
//        },
        TFB_LIKE_MAP {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/tfblike/map");
                request.headers().add(HttpHeaderNames.ACCEPT, "application/json");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                String expectedResponseBody = """
{"message":"Hello, World!"}""";
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
        };

        abstract FullHttpRequest request();

        abstract void verifyResponse(FullHttpResponse response);
    }

    @Controller("/tfblike")
    @Requires(property = "spec.name", value = "ControllersBenchmark")
    static class TfbLikeController {

        public static final String STRING = "Hello, World!";
        private static final byte[] BYTES = STRING.getBytes(StandardCharsets.UTF_8);
        public static final List<@NotNull SomeBean1> BEANS1 = List.of(
            new SomeBean1(1, "A"),
            new SomeBean1(2, "B"),
            new SomeBean1(3, "C")
        );
        public static final List<@NotNull SomeBean2> BEANS2 = List.of(
            new SomeBean2(1, 123),
            new SomeBean2(2, 456),
            new SomeBean2(3, 789)
        );

        @Get(value = "/bytes", produces = MediaType.TEXT_PLAIN)
        public byte[] bytes() {
            return BYTES;
        }
        @Get(value = "/string", produces = MediaType.TEXT_PLAIN)
        public String string() {
            return STRING;
        }

        @Get("/beans1")
        public List<SomeBean1> beans1() {
            return BEANS1;
        }

        @Get("/beans2")
        public List<SomeBean2> beans2() {
            return BEANS2;
        }

        @Get("/async/beans1")
        public CompletionStage<List<SomeBean1>> asyncBeans1() {
            return CompletableFuture.completedFuture(BEANS1);
        }

        @Get("/async/beans2")
        public CompletionStage<List<SomeBean2>> asyncBeans2() {
            return CompletableFuture.completedFuture(BEANS2);
        }

        @SingleResult
        @Get("/reactive/beans1")
        public Publisher<List<SomeBean1>> publisherBeans1() {
            return Mono.just(BEANS1);
        }

        @Get("/reactive/beans2")
        public Mono<List<SomeBean2>> publisherBeans2() {
            return Mono.just(BEANS2);
        }

        @Get("/map")
        public Map<String, String> getJson() {
            final Map<String, String> map = new HashMap<>();
            map.put("message", STRING);
            return map;
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

    public record SomeBean1(int id, String message) {
    }

    public static class SomeBean2 {
        private int id;
        private int randomNumber;

        public SomeBean2() {
        }

        public SomeBean2(int id, int randomNumber) {
            this.id = id;
            this.randomNumber = randomNumber;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getRandomNumber() {
            return randomNumber;
        }

        public void setRandomNumber(int randomNumber) {
            this.randomNumber = randomNumber;
        }
    }
}
