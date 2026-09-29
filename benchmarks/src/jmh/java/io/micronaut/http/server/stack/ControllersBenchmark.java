package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Order;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.RequestBean;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.server.EmbeddedServer;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import reactor.core.publisher.Flux;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
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
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void test(Holder holder) {
        ByteBuf response = holder.exchange();
        BenchOptions.verifyResponse(holder.responseBytes, response);
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
            Map<String, Object> properties = BenchOptions.serverProperties("ControllersBenchmark");
            properties.putAll(request.properties());
            ctx = ApplicationContext.run(properties);
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
        },
        /**
         * A path variable, a header and a query value bound to one method.
         */
        PATH_HEADER_QUERY {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ctrl/books/42?page=3");
                request.headers().add(HttpHeaderNames.ACCEPT, "application/json");
                request.headers().add("X-Tenant", "acme");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                verifyJson(response, """
{"id":42,"tenant":"acme","page":3}""");
            }
        },
        /**
         * Arguments without binding annotations, bound from the query by the unmatched binder chain.
         */
        UNANNOTATED_QUERY {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ctrl/unannotated?a=x&b=y&c=3");
                request.headers().add(HttpHeaderNames.ACCEPT, "text/plain");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                verifyText(response, HttpResponseStatus.OK, "xy3");
            }
        },
        /**
         * A {@code @RequestBean} with a path variable, a header and a query value.
         */
        REQUEST_BEAN {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ctrl/bean/42?page=3");
                request.headers().add(HttpHeaderNames.ACCEPT, "application/json");
                request.headers().add("X-Tenant", "acme");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                verifyJson(response, """
{"id":42,"tenant":"acme","page":3}""");
            }
        },
        /**
         * A request that matches no route, in an application without status routes.
         */
        NOT_FOUND {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ctrl/missing");
                request.headers().add(HttpHeaderNames.ACCEPT, "application/json");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
                Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            }
        },
        /**
         * A streamed {@code Flux} from a route with {@code @ExecuteOn}. The executor runs tasks
         * inline, so the benchmark measures the executor lookup and wrapping, not a thread hop.
         */
        STREAM_EXECUTE_ON {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ctrl/stream");
                request.headers().add(HttpHeaderNames.ACCEPT, "application/json");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                Assertions.assertEquals("""
[{"id":1,"message":"A"},{"id":2,"message":"B"},{"id":3,"message":"C"}]""", response.content().toString(StandardCharsets.UTF_8));
            }
        },
        /**
         * A route under five pattern filters (three that match, one on another path and one on
         * another method) and one {@code @FilterMatcher} filter.
         */
        FILTERED {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/filtered");
                request.headers().add(HttpHeaderNames.ACCEPT, "text/plain");
                return request;
            }

            @Override
            Map<String, Object> properties() {
                return Map.of("bench.filters", true);
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                verifyText(response, HttpResponseStatus.OK, "filtered");
                Assertions.assertEquals(List.of("1", "2", "3"), response.headers().getAll("X-Api-Filter").stream().sorted().toList());
                Assertions.assertEquals("true", response.headers().get("X-Matched"));
                Assertions.assertNull(response.headers().get("X-Admin"));
                Assertions.assertNull(response.headers().get("X-Post"));
            }
        },
        /**
         * An exception handled by a local {@code @Error} route.
         */
        ERROR_ROUTE {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ctrl/error");
                request.headers().add(HttpHeaderNames.ACCEPT, "text/plain");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                verifyText(response, HttpResponseStatus.CONFLICT, "error route");
            }
        },
        /**
         * An exception handled by an {@code ExceptionHandler} bean.
         */
        EXCEPTION_HANDLER {
            @Override
            FullHttpRequest request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ctrl/handled");
                request.headers().add(HttpHeaderNames.ACCEPT, "text/plain");
                return request;
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                verifyText(response, HttpResponseStatus.CONFLICT, "exception handler");
            }
        };

        abstract FullHttpRequest request();

        abstract void verifyResponse(FullHttpResponse response);

        /**
         * @return Extra application properties for this scenario
         */
        Map<String, Object> properties() {
            return Map.of();
        }

        static void verifyJson(FullHttpResponse response, String expectedResponseBody) {
            Assertions.assertEquals(HttpResponseStatus.OK, response.status());
            Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            Assertions.assertEquals(expectedResponseBody, response.content().toString(StandardCharsets.UTF_8));
            Assertions.assertEquals(expectedResponseBody.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
        }

        static void verifyText(FullHttpResponse response, HttpResponseStatus status, String expectedResponseBody) {
            Assertions.assertEquals(status, response.status());
            Assertions.assertEquals("text/plain", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            Assertions.assertEquals(expectedResponseBody, response.content().toString(StandardCharsets.UTF_8));
            Assertions.assertEquals(expectedResponseBody.length(), response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
        }
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

        @Get("/books/{id}")
        Book book(Long id, @Header("X-Tenant") String tenant, @QueryValue int page) {
            return new Book(id, tenant, page);
        }

        @Get(value = "/unannotated", produces = MediaType.TEXT_PLAIN)
        String unannotated(String a, String b, int c) {
            return a + b + c;
        }

        @Get("/bean/{id}")
        Book bean(@RequestBean BookRequest request) {
            return new Book(request.id(), request.tenant(), request.page());
        }

        @ExecuteOn(InlineExecutorFactory.NAME)
        @Get("/stream")
        Flux<SomeBean1> stream() {
            return Flux.fromIterable(TfbLikeController.BEANS1);
        }

        @Get(value = "/error", produces = MediaType.TEXT_PLAIN)
        String error() {
            throw new BenchException();
        }

        @Get(value = "/handled", produces = MediaType.TEXT_PLAIN)
        String handled() {
            throw new BenchHandledException();
        }

        @Error(BenchException.class)
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> onError(BenchException e) {
            return HttpResponse.status(HttpStatus.CONFLICT).body("error route");
        }
    }

    public record Book(Long id, String tenant, int page) {
    }

    @Introspected
    public record BookRequest(@PathVariable Long id, @Header("X-Tenant") String tenant, @QueryValue int page) {
    }

    /**
     * An exception without a stack trace, so that creating it does not dominate the benchmark.
     */
    static final class BenchException extends RuntimeException {
        BenchException() {
            super(null, null, false, false);
        }
    }

    /**
     * An exception without a stack trace, handled by {@link BenchExceptionHandler}.
     */
    static final class BenchHandledException extends RuntimeException {
        BenchHandledException() {
            super(null, null, false, false);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ControllersBenchmark")
    static final class BenchExceptionHandler implements ExceptionHandler<BenchHandledException, HttpResponse<String>> {
        @Override
        public HttpResponse<String> handle(HttpRequest request, BenchHandledException exception) {
            return HttpResponse.status(HttpStatus.CONFLICT).contentType(MediaType.TEXT_PLAIN_TYPE).body("exception handler");
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "ControllersBenchmark")
    static class InlineExecutorFactory {
        static final String NAME = "bench-inline";

        @Singleton
        @Named(NAME)
        ExecutorService inlineExecutor() {
            return new AbstractExecutorService() {
                private volatile boolean shutdown;

                @Override
                public void execute(Runnable command) {
                    command.run();
                }

                @Override
                public void shutdown() {
                    shutdown = true;
                }

                @Override
                public List<Runnable> shutdownNow() {
                    shutdown = true;
                    return List.of();
                }

                @Override
                public boolean isShutdown() {
                    return shutdown;
                }

                @Override
                public boolean isTerminated() {
                    return shutdown;
                }

                @Override
                public boolean awaitTermination(long timeout, TimeUnit unit) {
                    return true;
                }
            };
        }
    }

    /**
     * The route of the {@link Request#FILTERED} scenario carries this, so {@link MatchedFilter} applies to it.
     */
    @FilterMatcher
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface BenchFiltered {
    }

    @Controller("/api")
    @BenchFiltered
    @Requires(property = "spec.name", value = "ControllersBenchmark")
    static class FilteredController {
        @Get(value = "/filtered", produces = MediaType.TEXT_PLAIN)
        String filtered() {
            return "filtered";
        }
    }

    @ServerFilter("/api/**")
    @Requires(property = "bench.filters", value = "true")
    static class ApiFilter1 {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Api-Filter", "1");
        }
    }

    @ServerFilter("/api/**")
    @Requires(property = "bench.filters", value = "true")
    static class ApiFilter2 {
        @Order(1)
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Api-Filter", "2");
        }
    }

    @ServerFilter("/api/**")
    @Requires(property = "bench.filters", value = "true")
    static class ApiFilter3 {
        @Order(2)
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Api-Filter", "3");
        }
    }

    @ServerFilter("/admin/**")
    @Requires(property = "bench.filters", value = "true")
    static class AdminFilter {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Admin", "true");
        }
    }

    @ServerFilter(value = "/api/**", methods = io.micronaut.http.HttpMethod.POST)
    @Requires(property = "bench.filters", value = "true")
    static class PostFilter {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Post", "true");
        }
    }

    @BenchFiltered
    @ServerFilter(Filter.MATCH_ALL_PATTERN)
    @Requires(property = "bench.filters", value = "true")
    static class MatchedFilter {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Matched", "true");
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
