package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.builder.RequestCondition;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.nio.charset.StandardCharsets;

/**
 * The full HTTP stack for handler routes: a plain route, a route of a located target, and a
 * route chosen by the body of the request, to compare the cost of route matching.
 */
public class HandlerRoutesBenchmark {

    @Benchmark
    public void test(Holder holder) {
        ByteBuf response = holder.exchange();
        BenchOptions.verifyResponse(holder.responseBytes, response);
        response.release();
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({"PLAIN", "LOCATED", "BODY_CONDITION"})
        Scenario scenario = Scenario.PLAIN;

        ApplicationContext ctx;
        EmbeddedChannel channel;
        ByteBuf requestBytes;
        ByteBuf responseBytes;

        @Setup
        public void setUp() {
            ctx = ApplicationContext.run(BenchOptions.serverProperties("HandlerRoutesBenchmark"));
            channel = ((NettyHttpServer) ctx.getBean(EmbeddedServer.class)).buildEmbeddedChannel(false);
            channel.freezeTime();

            EmbeddedChannel clientChannel = new EmbeddedChannel();
            clientChannel.pipeline().addLast(new HttpClientCodec());
            clientChannel.pipeline().addLast(new HttpObjectAggregator(1000));
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, scenario.path,
                Unpooled.wrappedBuffer("{\"kind\":\"express\",\"id\":1}".getBytes(StandardCharsets.UTF_8)));
            request.headers().add(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
            request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            clientChannel.writeOutbound(request);
            clientChannel.flushOutbound();
            requestBytes = NettyUtil.readAllOutboundContiguous(clientChannel);

            responseBytes = exchange();
            clientChannel.writeInbound(responseBytes.retainedDuplicate());
            FullHttpResponse response = clientChannel.readInbound();
            Assertions.assertEquals(HttpResponseStatus.OK, response.status());
            Assertions.assertEquals(scenario.name(), response.content().toString(StandardCharsets.UTF_8));
            response.release();
        }

        private ByteBuf exchange() {
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
        PLAIN("/plain"),
        LOCATED("/located/1/items"),
        BODY_CONDITION("/body");

        final String path;

        Scenario(String path) {
            this.path = path;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "HandlerRoutesBenchmark")
    static final class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/plain", (request, pathVariables) -> HttpResponse.ok("PLAIN"));
            LocatedRoutes<String> items = new LocatedRoutes<>() {
                @Override
                public Argument<String> targetType() {
                    return Argument.STRING;
                }

                @Override
                public void routes(LocatedHttpRouteBuilder<String> located) {
                    located.POST("/items").handle((request, pathVariables, target) -> HttpResponse.ok("LOCATED"));
                }
            };
            routes.locate("/located/{id}", (request, pathVariables) -> pathVariables.getString("id"), target -> items);
            // BODY:START
            routes.POST("/body").where(RequestCondition.bodyText(text -> text.contains("express"))).order(-1)
                .handle((request, pathVariables) -> HttpResponse.ok("BODY_CONDITION"));
            routes.POST("/body").handle((request, pathVariables) -> HttpResponse.ok("other"));
            // BODY:END
        }
    }
}
