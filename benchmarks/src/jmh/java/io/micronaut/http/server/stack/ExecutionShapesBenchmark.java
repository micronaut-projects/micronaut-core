package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.RouteExecutionModes;
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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The full HTTP stack for the execution shapes of routes: what a route returns, and how the
 * server turns it into a response, with the same small body, to compare the cost of invoking a
 * route and adapting its result. The {@code MIXED} scenario sends the requests of every shape in
 * turn, so the call sites of the execution see every shape instead of one.
 */
public class ExecutionShapesBenchmark {

    @Benchmark
    public void test(Holder holder) {
        holder.next();
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({
            "STRING", "RESPONSE", "OPTIONAL", "HANDLER", "HANDLER_ASYNC", "STAGE", "STAGE_RESPONSE",
            "MONO", "MONO_OPERATOR", "MONO_RESPONSE", "RESPONSE_MONO", "FLUX", "OBJECT_MONO", "MIXED"
        })
        String scenario = "STRING";

        /**
         * {@code PREPARED}, the execution prepared for each route, or {@code COMPATIBILITY}, which
         * reads the route on every request: the same routes, filters and responses.
         */
        @Param({"PREPARED", "COMPATIBILITY"})
        String mode = "PREPARED";

        ApplicationContext ctx;
        EmbeddedChannel channel;
        Scenario[] scenarios;
        ByteBuf[] requestBytes;
        ByteBuf[] responseBytes;
        int index;

        @Setup
        public void setUp() {
            ctx = ApplicationContext.run(BenchOptions.serverProperties("ExecutionShapesBenchmark"));
            RouteExecutionModes.select(ctx, mode.equals("COMPATIBILITY"));
            channel = ((NettyHttpServer) ctx.getBean(EmbeddedServer.class)).buildEmbeddedChannel(false);
            channel.freezeTime();
            scenarios = scenario.equals("MIXED") ? Scenario.values() : new Scenario[]{Scenario.valueOf(scenario)};
            requestBytes = new ByteBuf[scenarios.length];
            responseBytes = new ByteBuf[scenarios.length];
            for (int i = 0; i < scenarios.length; i++) {
                Scenario s = scenarios[i];
                EmbeddedChannel clientChannel = new EmbeddedChannel();
                clientChannel.pipeline().addLast(new HttpClientCodec());
                clientChannel.pipeline().addLast(new HttpObjectAggregator(10_000));
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, s.path, Unpooled.EMPTY_BUFFER);
                request.headers().add(HttpHeaderNames.CONTENT_LENGTH, 0);
                clientChannel.writeOutbound(request);
                clientChannel.flushOutbound();
                requestBytes[i] = NettyUtil.readAllOutboundContiguous(clientChannel);

                responseBytes[i] = exchange(requestBytes[i]);
                clientChannel.writeInbound(responseBytes[i].retainedDuplicate());
                FullHttpResponse response = clientChannel.readInbound();
                Assertions.assertEquals(HttpResponseStatus.OK, response.status(), s.path);
                Assertions.assertEquals(s.body, response.content().toString(StandardCharsets.UTF_8), s.path);
                response.release();
                clientChannel.finishAndReleaseAll();
            }
        }

        void next() {
            int i = index;
            index = i + 1 == scenarios.length ? 0 : i + 1;
            ByteBuf response = exchange(requestBytes[i]);
            // the whole response, the streamed body included
            BenchOptions.verifyResponse(responseBytes[i], response);
            response.release();
        }

        private ByteBuf exchange(ByteBuf request) {
            channel.writeInbound(request.retainedDuplicate());
            channel.runPendingTasks();
            return NettyUtil.readAllOutboundComposite(channel);
        }

        @TearDown
        public void tearDown() {
            ctx.close();
            for (int i = 0; i < scenarios.length; i++) {
                requestBytes[i].release();
                responseBytes[i].release();
            }
        }
    }

    public enum Scenario {
        STRING("/shapes/string", "ok"),
        RESPONSE("/shapes/response", "ok"),
        OPTIONAL("/shapes/optional", "ok"),
        HANDLER("/handler", "ok"),
        HANDLER_ASYNC("/handler-async", "ok"),
        STAGE("/shapes/stage", "ok"),
        STAGE_RESPONSE("/shapes/stage-response", "ok"),
        MONO("/shapes/mono", "ok"),
        MONO_OPERATOR("/shapes/mono-operator", "ok"),
        MONO_RESPONSE("/shapes/mono-response", "ok"),
        RESPONSE_MONO("/shapes/response-mono", "ok"),
        // the strings of a stream are written as they are, unquoted
        FLUX("/shapes/flux", "[o,k]"),
        OBJECT_MONO("/shapes/object-mono", "ok");

        final String path;
        final String body;

        Scenario(String path, String body) {
            this.path = path;
            this.body = body;
        }
    }

    @Controller("/shapes")
    @Requires(property = "spec.name", value = "ExecutionShapesBenchmark")
    static class ShapesController {

        @Get(value = "/string", produces = MediaType.TEXT_PLAIN)
        String string() {
            return "ok";
        }

        @Get(value = "/response", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> response() {
            return HttpResponse.ok("ok");
        }

        @Get(value = "/optional", produces = MediaType.TEXT_PLAIN)
        Optional<String> optional() {
            return Optional.of("ok");
        }

        @Get(value = "/stage", produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> stage() {
            return CompletableFuture.completedFuture("ok");
        }

        @Get(value = "/stage-response", produces = MediaType.TEXT_PLAIN)
        CompletionStage<HttpResponse<String>> stageResponse() {
            return CompletableFuture.completedFuture(HttpResponse.ok("ok"));
        }

        @Get(value = "/mono", produces = MediaType.TEXT_PLAIN)
        Mono<String> mono() {
            return Mono.just("ok");
        }

        @Get(value = "/mono-operator", produces = MediaType.TEXT_PLAIN)
        Mono<String> monoOperator() {
            return Mono.just("o").map(o -> o + "k");
        }

        @Get(value = "/mono-response", produces = MediaType.TEXT_PLAIN)
        Mono<HttpResponse<String>> monoResponse() {
            return Mono.just(HttpResponse.ok("ok"));
        }

        @Get(value = "/response-mono", produces = MediaType.TEXT_PLAIN)
        HttpResponse<Mono<String>> responseMono() {
            return HttpResponse.ok(Mono.just("ok"));
        }

        @Get("/flux")
        Flux<String> flux() {
            return Flux.just("o", "k");
        }

        @Get(value = "/object-mono", produces = MediaType.TEXT_PLAIN)
        Object objectMono() {
            return Mono.just("ok");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ExecutionShapesBenchmark")
    static final class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/handler").produces(MediaType.TEXT_PLAIN_TYPE).handle((request, pathVariables) -> HttpResponse.ok("ok"));
            routes.GET("/handler-async").produces(MediaType.TEXT_PLAIN_TYPE)
                .handleAsync((request, pathVariables) -> CompletableFuture.completedFuture(HttpResponse.ok("ok")));
        }
    }
}
