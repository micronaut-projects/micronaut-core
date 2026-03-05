package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import jakarta.inject.Singleton;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FormBodyQuirkTest {
    // test the functioning of the new multipart parser api and its quirks.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void test(boolean quirk) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "FormBodyQuirkTest",
            "micronaut.server.port", "0",
            "micronaut.server.netty.form-decoder-quirks", quirk ? List.of("forward-chunk-cr") : List.of()
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class)) {
            server.start();

            CompletableFuture<String> response = new CompletableFuture<>();
            Channel channel = new Bootstrap()
                .channel(NioSocketChannel.class)
                .group(ctx.getBean(EventLoopGroup.class))
                .handler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(1024), new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                try {
                                    FullHttpResponse r = (FullHttpResponse) msg;
                                    response.complete(r.content().toString(StandardCharsets.UTF_8));
                                } catch (Exception e) {
                                    response.completeExceptionally(e);
                                } finally {
                                    ReferenceCountUtil.release(msg);
                                }
                            }
                        });
                    }
                })
                .connect(server.getHost(), server.getPort()).sync().channel();

            DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/quirk-echo");
            request.headers().add(HttpHeaderNames.HOST, "example.com");
            request.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=\"xyz\"");
            request.headers().add(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
            channel.write(request, channel.voidPromise());
            channel.writeAndFlush(new DefaultHttpContent(ByteBufUtil.writeUtf8(channel.alloc(), """
                --xyz\r
                Content-Disposition: form-data; name="foo"\r
                \r
                bar\r"""))).sync();
            ctx.getBean(MyFilter.class).requestReceived.get();
            channel.writeAndFlush(new DefaultLastHttpContent(ByteBufUtil.writeUtf8(channel.alloc(), "\n--xyz--"))).sync();

            assertEquals(quirk ? "bar\r" : "bar", response.get());
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "FormBodyQuirkTest")
    public static class MyController {
        @Post("/quirk-echo")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        public String echo(@Part("foo") String foo) {
            return foo;
        }
    }

    @Singleton
    @ServerFilter
    @Requires(property = "spec.name", value = "FormBodyQuirkTest")
    public static class MyFilter {
        final CompletableFuture<?> requestReceived = new CompletableFuture<>();

        @RequestFilter("/quirk-echo")
        public void onRequest() {
            requestReceived.complete(null);
        }
    }
}
