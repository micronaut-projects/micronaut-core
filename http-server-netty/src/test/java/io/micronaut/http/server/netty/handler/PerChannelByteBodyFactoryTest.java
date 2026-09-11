package io.micronaut.http.server.netty.handler;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PerChannelByteBodyFactoryTest {
    @Test
    void requestsOnOneChannelShareTheBodyFactory() {
        List<NettyHttpRequest<?>> requests = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            public void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                requests.add(new NettyHttpRequest<>(request, body, ctx, ConversionService.SHARED, new HttpServerConfiguration()));
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty());
            }

            @Override
            public void handleUnboundError(Throwable cause) {
                throw new AssertionError(cause);
            }
        }));

        channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/a"));
        channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/b"));
        channel.checkException();

        assertEquals(2, requests.size());
        NettyByteBodyFactory shared = NettyByteBodyFactory.forChannel(channel);
        for (NettyHttpRequest<?> request : requests) {
            // one lookup per request, one instance per channel
            assertSame(request.byteBodyFactory(), request.byteBodyFactory());
            assertSame(shared, request.byteBodyFactory());
            request.release();
        }
        channel.finishAndReleaseAll();
    }
}
