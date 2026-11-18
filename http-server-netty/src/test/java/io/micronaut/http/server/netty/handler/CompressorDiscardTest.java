package io.micronaut.http.server.netty.handler;

import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompressorDiscardTest {
    @Test
    void discardAfterFinishReleasesQueuedOutputAndIsIdempotent() {
        var channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        try {
            var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
            var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            var session = new Compressor(new HttpCompressionStrategy() {
                @Override
                public boolean isEnabled() {
                    return true;
                }

                @Override
                public int getMaxZstdEncodeSize() {
                    return Integer.MAX_VALUE;
                }

                @Override
                public boolean shouldCompress(HttpResponse ignored) {
                    return true;
                }
            }).prepare(channel.pipeline().firstContext(), request, response, 3);
            assertNotNull(session);
            var input = Unpooled.wrappedBuffer(new byte[]{1, 2, 3});
            session.push(input);
            session.finish();
            session.discard();
            session.discard();
            assertEquals(0, input.refCnt());
            assertNull(session.poll());
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
