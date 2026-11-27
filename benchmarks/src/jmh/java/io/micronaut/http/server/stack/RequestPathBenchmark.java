package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
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
 * One HTTP/1.1 exchange through the server's pipeline on an embedded channel, for the request
 * shapes whose cost is the server's own per-request work rather than the route: a small text
 * response to a request that accepts compression, as a browser or an HTTP client sends it, and a
 * response with no body. Run it with {@code -Pjmh.profilers=gc} for the bytes allocated per
 * exchange.
 */
public class RequestPathBenchmark {

    @Benchmark
    public void test(Holder holder) {
        ByteBuf response = holder.exchange();
        BenchOptions.verifyResponse(holder.responseBytes, response);
        response.release();
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({"SMALL_TEXT", "SMALL_TEXT_ACCEPTS_COMPRESSION", "NO_CONTENT"})
        Scenario scenario = Scenario.SMALL_TEXT;

        ApplicationContext ctx;
        EmbeddedChannel channel;
        ByteBuf requestBytes;
        ByteBuf responseBytes;

        @Setup
        public void setUp() {
            ctx = ApplicationContext.run(BenchOptions.serverProperties("RequestPathBenchmark"));
            channel = ((NettyHttpServer) ctx.getBean(EmbeddedServer.class)).buildEmbeddedChannel(false);
            channel.freezeTime();

            EmbeddedChannel clientChannel = new EmbeddedChannel();
            clientChannel.pipeline().addLast(new HttpClientCodec());
            clientChannel.pipeline().addLast(new HttpObjectAggregator(1000));
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, scenario.path);
            request.headers().add(HttpHeaderNames.HOST, "localhost");
            if (scenario.acceptEncoding != null) {
                request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, scenario.acceptEncoding);
            }
            clientChannel.writeOutbound(request);
            clientChannel.flushOutbound();
            requestBytes = NettyUtil.readAllOutboundContiguous(clientChannel);

            responseBytes = exchange();
            clientChannel.writeInbound(responseBytes.retainedDuplicate());
            FullHttpResponse response = clientChannel.readInbound();
            Assertions.assertEquals(scenario.status, response.status());
            Assertions.assertFalse(response.headers().contains(HttpHeaderNames.CONTENT_ENCODING));
            Assertions.assertEquals(scenario.body, response.content().toString(StandardCharsets.UTF_8));
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
        SMALL_TEXT("/text", null, HttpResponseStatus.OK, "Hello, World!"),
        // what Chrome sends
        SMALL_TEXT_ACCEPTS_COMPRESSION("/text", "gzip, deflate, br, zstd", HttpResponseStatus.OK, "Hello, World!"),
        NO_CONTENT("/empty", null, HttpResponseStatus.NO_CONTENT, "");

        final String path;
        final String acceptEncoding;
        final HttpResponseStatus status;
        final String body;

        Scenario(String path, String acceptEncoding, HttpResponseStatus status, String body) {
            this.path = path;
            this.acceptEncoding = acceptEncoding;
            this.status = status;
            this.body = body;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RequestPathBenchmark")
    static final class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/text", (request, pathVariables) -> HttpResponse.ok("Hello, World!"));
            routes.GET("/empty", (request, pathVariables) -> HttpResponse.noContent());
        }
    }
}
