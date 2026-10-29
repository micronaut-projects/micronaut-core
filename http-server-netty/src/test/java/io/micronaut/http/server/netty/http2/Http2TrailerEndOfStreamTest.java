package io.micronaut.http.server.netty.http2;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrame;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that HTTP/2 requests are handled correctly in the non-legacy multiplex handler path,
 * covering both normal END_STREAM on DATA and END_STREAM on trailing HEADERS.
 */
class Http2TrailerEndOfStreamTest {

    private static final String JSON_PAYLOAD = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"eth_blockNumber\"}";

    @Test
    void uncompressedBodyWithTrailers() throws Exception {
        try (EmbeddedServer server = startServer()) {
            byte[] payload = JSON_PAYLOAD.getBytes(StandardCharsets.UTF_8);
            H2Response response = sendRequest(server, payload, null, true);
            assertEquals(200, response.statusCode);
            assertEquals("{\"method\":\"eth_blockNumber\"}", response.body);
        }
    }

    @Test
    void gzipCompressedBodyWithTrailers() throws Exception {
        try (EmbeddedServer server = startServer()) {
            byte[] compressed = gzip(JSON_PAYLOAD.getBytes(StandardCharsets.UTF_8));
            H2Response response = sendRequest(server, compressed, "gzip", true);
            assertEquals(200, response.statusCode);
            assertEquals("{\"method\":\"eth_blockNumber\"}", response.body);
        }
    }

    @Test
    void gzipCompressedBodyWithoutTrailers() throws Exception {
        try (EmbeddedServer server = startServer()) {
            byte[] compressed = gzip(JSON_PAYLOAD.getBytes(StandardCharsets.UTF_8));
            H2Response response = sendRequest(server, compressed, "gzip", false);
            assertEquals(200, response.statusCode);
            assertEquals("{\"method\":\"eth_blockNumber\"}", response.body);
        }
    }

    private static EmbeddedServer startServer() {
        return ApplicationContext.run(
                EmbeddedServer.class,
                Map.of(
                        "spec", "Http2TrailerEndOfStreamTest",
                        "micronaut.server.http-version", "2.0",
                        "micronaut.server.netty.legacy-multiplex-handlers", false,
                        "micronaut.server.port", -1
                )
        );
    }

    private static H2Response sendRequest(
            EmbeddedServer server, byte[] payload, String contentEncoding, boolean useTrailers) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        CompletableFuture<H2Response> responseFuture = new CompletableFuture<>();

        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build());
                            ch.pipeline().addLast(new Http2MultiplexHandler(new SimpleChannelInboundHandler<>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                }
                            }));
                        }
                    });

            Channel parent = bootstrap.connect(server.getHost(), server.getPort()).sync().channel();

            Http2StreamChannel stream = new Http2StreamChannelBootstrap(parent)
                    .handler(new SimpleChannelInboundHandler<Http2StreamFrame>() {
                        private final StringBuilder body = new StringBuilder();
                        private int statusCode = 0;

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) {
                            if (msg instanceof Http2HeadersFrame headersFrame) {
                                CharSequence status = headersFrame.headers().status();
                                if (status != null) {
                                    statusCode = Integer.parseInt(status.toString());
                                }
                                if (headersFrame.isEndStream()) {
                                    responseFuture.complete(new H2Response(statusCode, body.toString()));
                                }
                            } else if (msg instanceof Http2DataFrame dataFrame) {
                                body.append(dataFrame.content().toString(StandardCharsets.UTF_8));
                                if (dataFrame.isEndStream()) {
                                    responseFuture.complete(new H2Response(statusCode, body.toString()));
                                }
                            } else if (msg instanceof Http2ResetFrame resetFrame) {
                                responseFuture.completeExceptionally(
                                        new IllegalStateException("RST_STREAM: " + resetFrame.errorCode())
                                );
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            responseFuture.completeExceptionally(cause);
                        }
                    })
                    .open()
                    .sync()
                    .getNow();

            Http2Headers headers = new DefaultHttp2Headers()
                    .method(HttpMethod.POST.asciiName())
                    .scheme("http")
                    .authority(server.getHost() + ":" + server.getPort())
                    .path("/h2-trailer-test/echo")
                    .add("content-type", MediaType.APPLICATION_JSON);

            if (contentEncoding != null) {
                headers.add("content-encoding", contentEncoding);
            }

            stream.write(new DefaultHttp2HeadersFrame(headers, false));

            // Split body across two DATA frames
            int split = payload.length / 2;
            ByteBuf first = Unpooled.wrappedBuffer(payload, 0, split);
            ByteBuf second = Unpooled.wrappedBuffer(payload, split, payload.length - split);

            stream.write(new DefaultHttp2DataFrame(first, false));

            if (useTrailers) {
                // Last DATA does NOT carry END_STREAM; trailing HEADERS do
                stream.write(new DefaultHttp2DataFrame(second, false));
                Http2Headers trailers = new DefaultHttp2Headers()
                        .add("x-envoy-peer-metadata", "test");
                stream.writeAndFlush(new DefaultHttp2HeadersFrame(trailers, true)).sync();
            } else {
                // Normal case: last DATA carries END_STREAM
                stream.writeAndFlush(new DefaultHttp2DataFrame(second, true)).sync();
            }

            return responseFuture.get(5, TimeUnit.SECONDS);
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    record H2Response(int statusCode, String body) {}

    record JsonRpcRequest(String jsonrpc, int id, String method) {}

    @Requires(property = "spec", value = "Http2TrailerEndOfStreamTest")
    @Controller("/h2-trailer-test")
    static class EchoController {
        @Post(value = "/echo", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
        HttpResponse<String> echo(@Body JsonRpcRequest request) {
            return HttpResponse.ok("{\"method\":\"" + request.method() + "\"}");
        }
    }
}
