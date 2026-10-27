/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.netty.buffer.ByteBuf;
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
import io.netty.handler.codec.http.HttpVersion;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handler routes and the same endpoints as controller methods.
 */
public class HandlerRoutesStackBenchmark {

    private static final String SPEC = "HandlerRoutesStackBenchmark";
    private static final String SEARCH_BODY = "{\"haystack\": [\"xniomb\", \"seelzp\", \"nzogdq\", \"omblsg\", \"idgtlm\", \"ydonzo\"], \"needle\": \"idg\"}";

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(HandlerRoutesStackBenchmark.class.getName() + ".*")
            .warmupIterations(5)
            .measurementIterations(10)
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
        Scenario scenario;

        ApplicationContext ctx;
        EmbeddedChannel channel;
        ByteBuf requestBytes;
        ByteBuf responseBytes;

        @Setup
        public void setUp() {
            Map<String, Object> properties = BenchOptions.serverProperties(SPEC);
            properties.put("micronaut.python.enabled", false);
            ctx = ApplicationContext.run(properties);
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            channel = ((NettyHttpServer) server).buildEmbeddedChannel(false);
            channel.freezeTime();

            EmbeddedChannel clientChannel = new EmbeddedChannel();
            clientChannel.pipeline().addLast(new HttpClientCodec());
            clientChannel.pipeline().addLast(new HttpObjectAggregator(1_000_000));
            clientChannel.writeOutbound(scenario.request());
            clientChannel.flushOutbound();
            requestBytes = NettyUtil.readAllOutboundContiguous(clientChannel);

            responseBytes = exchange();
            clientChannel.writeInbound(responseBytes.retainedDuplicate());
            FullHttpResponse response = clientChannel.readInbound();
            try {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals(scenario.contentType, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                Assertions.assertEquals(scenario.expected, response.content().toString(StandardCharsets.UTF_8));
            } finally {
                response.release();
            }
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

    public enum Scenario {
        FN_TEXT("/fn/hello", null, "text/plain", "Hello World"),
        FN_PATH_JSON("/fn/items/42", null, "application/json", "{\"id\":42,\"name\":\"item\"}"),
        FN_POST_JSON("/fn/search", SEARCH_BODY, "application/json", "{\"listIndex\":4,\"stringIndex\":0}"),
        FN_ASYNC_TEXT("/fn/async", null, "text/plain", "Hello World"),
        CTL_TEXT("/ctl/hello", null, "text/plain", "Hello World"),
        CTL_PATH_JSON("/ctl/items/42", null, "application/json", "{\"id\":42,\"name\":\"item\"}"),
        CTL_POST_JSON("/ctl/search", SEARCH_BODY, "application/json", "{\"listIndex\":4,\"stringIndex\":0}"),
        FN_FILTERED("/fn/filtered/hello", null, "text/plain", "Hello World"),
        CTL_FILTERED("/ctl/filtered/hello", null, "text/plain", "Hello World");

        final String uri;
        final String body;
        final String contentType;
        final String expected;

        Scenario(String uri, String body, String contentType, String expected) {
            this.uri = uri;
            this.body = body;
            this.contentType = contentType;
            this.expected = expected;
        }

        FullHttpRequest request() {
            FullHttpRequest request;
            if (body == null) {
                request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
            } else {
                request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri,
                    Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8)));
                request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            }
            request.headers().add(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
            request.headers().add(HttpHeaderNames.ACCEPT, contentType);
            return request;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/fn/hello", (request, pathVariables) -> HttpResponse.ok("Hello World").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/fn/items/{id}", (request, pathVariables) -> HttpResponse.ok(new Item(pathVariables.getInt("id"), "item")));
            routes.POST("/fn/search").body(Input.class).handle((request, pathVariables, input) -> find(input.haystack(), input.needle()));
            routes.GET("/fn/filtered/hello", (request, pathVariables) -> HttpResponse.ok("Hello World").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/fn/async").handleAsync((request, pathVariables) ->
                CompletableFuture.completedFuture(HttpResponse.ok("Hello World").contentType(MediaType.TEXT_PLAIN_TYPE)));
        }
    }

    @Controller("/ctl")
    @Requires(property = "spec.name", value = SPEC)
    static class HandlerRoutesStackBenchmarkController {
        @Get("/hello")
        @Produces(MediaType.TEXT_PLAIN)
        String hello() {
            return "Hello World";
        }

        @Get("/items/{id}")
        Item item(int id) {
            return new Item(id, "item");
        }

        @Post("/search")
        HttpResponse<Result> search(@Body Input input) {
            return find(input.haystack(), input.needle());
        }

        @Get("/filtered/hello")
        @Produces(MediaType.TEXT_PLAIN)
        String filteredHello() {
            return "Hello World";
        }
    }

    /**
     * A filter of some of the paths, which the router matches per request.
     */
    @ServerFilter("/**/filtered/**")
    @Requires(property = "spec.name", value = SPEC)
    static class HandlerRoutesStackBenchmarkFilter {
        @RequestFilter
        void request(HttpRequest<?> request) {
            request.setAttribute("filtered", Boolean.TRUE);
        }

        @ResponseFilter
        void response(MutableHttpResponse<?> response) {
            response.header("X-Filtered", "true");
        }
    }

    static HttpResponse<Result> find(List<String> haystack, String needle) {
        for (int listIndex = 0; listIndex < haystack.size(); listIndex++) {
            int stringIndex = haystack.get(listIndex).indexOf(needle);
            if (stringIndex != -1) {
                return HttpResponse.ok(new Result(listIndex, stringIndex));
            }
        }
        return HttpResponse.notFound();
    }

    @Introspected
    record Input(List<String> haystack, String needle) {
    }

    @Introspected
    record Result(int listIndex, int stringIndex) {
    }

    @Introspected
    record Item(int id, String name) {
    }
}
