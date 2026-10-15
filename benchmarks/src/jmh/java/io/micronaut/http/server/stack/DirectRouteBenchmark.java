package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
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
import io.netty.util.AsciiString;
import io.netty.util.concurrent.FastThreadLocalThread;
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
 * A {@code GET} answered by a direct route, compared with raw Netty and an ordinary functional
 * route: the whole server pipeline on an embedded channel, from the request bytes to the
 * response bytes.
 */
public class DirectRouteBenchmark {
    static final String SPEC_NAME = "DirectRouteBenchmark";
    static final byte[] HELLO = "Hello, World!".getBytes(StandardCharsets.UTF_8);

    @Benchmark
    public void test(Holder holder) {
        ByteBuf response = holder.exchange();
        BenchOptions.verifyResponse(holder.responseBytes, response);
        response.release();
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({"RAW_NETTY", "DIRECT_CONSTANT", "DIRECT_FUNCTION", "DIRECT_FUNCTION_TEXT", "DIRECT_JSON", "ORDINARY"})
        Variant variant = Variant.DIRECT_FUNCTION;

        AutoCloseable ctx;
        EmbeddedChannel channel;
        ByteBuf requestBytes;
        ByteBuf responseBytes;

        @Setup
        public void setUp() {
            if (FullHttpStackBenchmark.checkFtlThread && !(Thread.currentThread() instanceof FastThreadLocalThread)) {
                throw new IllegalStateException("Should run on a netty FTL thread");
            }
            if (variant == Variant.RAW_NETTY) {
                channel = new EmbeddedChannel(new HttpServerCodec(), new HttpObjectAggregator(10_000), new RawHandler());
                ctx = () -> { };
            } else {
                ApplicationContext context = ApplicationContext.run(BenchOptions.serverProperties(SPEC_NAME));
                EmbeddedServer server = context.getBean(EmbeddedServer.class);
                channel = ((NettyHttpServer) server).buildEmbeddedChannel(false);
                ctx = context;
            }
            channel.freezeTime();

            EmbeddedChannel clientChannel = new EmbeddedChannel(new HttpClientCodec(), new HttpObjectAggregator(1000));
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, variant.path);
            request.headers().add(HttpHeaderNames.HOST, "localhost");
            clientChannel.writeOutbound(request);
            clientChannel.flushOutbound();
            requestBytes = NettyUtil.readAllOutboundContiguous(clientChannel);

            responseBytes = exchange();
            clientChannel.writeInbound(responseBytes.retainedDuplicate());
            FullHttpResponse response = clientChannel.readInbound();
            Assertions.assertEquals(HttpResponseStatus.OK, response.status());
            Assertions.assertTrue(response.content().readableBytes() > 0);
            response.release();
        }

        ByteBuf exchange() {
            channel.writeInbound(requestBytes.retainedDuplicate());
            channel.runPendingTasks();
            return NettyUtil.readAllOutboundComposite(channel);
        }

        @TearDown
        public void tearDown() throws Exception {
            ctx.close();
            channel.finishAndReleaseAll();
            requestBytes.release();
            responseBytes.release();
        }
    }

    public enum Variant {
        RAW_NETTY("/plaintext"),
        DIRECT_CONSTANT("/constant"),
        DIRECT_FUNCTION("/plaintext"),
        DIRECT_FUNCTION_TEXT("/text"),
        DIRECT_JSON("/json"),
        ORDINARY("/ordinary");

        final String path;

        Variant(String path) {
            this.path = path;
        }
    }

    /**
     * What a hand-written Netty server does: a shared, unreleasable body, and the headers set
     * from constants.
     */
    static final class RawHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private static final ByteBuf BODY = Unpooled.unreleasableBuffer(Unpooled.directBuffer().writeBytes(HELLO)).asReadOnly();
        private static final AsciiString LENGTH = AsciiString.of(String.valueOf(HELLO.length));

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, BODY.duplicate(), false);
            response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
                .set(HttpHeaderNames.CONTENT_LENGTH, LENGTH);
            ctx.writeAndFlush(response, ctx.voidPromise());
        }
    }

    @Introspected
    public record Message(String message) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes, HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder builder) {
            builder.GET("/constant").respond(HttpResponse.ok(HELLO).contentType(MediaType.TEXT_PLAIN_TYPE));
            builder.GET("/plaintext").respond(direct -> HttpResponse.ok(HELLO).contentType(MediaType.TEXT_PLAIN_TYPE));
            builder.GET("/text").respond(direct -> HttpResponse.ok("Hello, World!").contentType(MediaType.TEXT_PLAIN_TYPE));
            builder.GET("/json").respond(direct -> HttpResponse.ok(new Message("Hello, World!")));
        }

        @Override
        public void routes(HttpRouteBuilder builder) {
            builder.GET("/ordinary", (request, pathVariables) -> HttpResponse.ok(HELLO).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
