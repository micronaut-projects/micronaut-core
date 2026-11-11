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
package io.micronaut.http.server.netty.handler;

import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A compression session must not create its encoder (a native zlib stream for gzip) before data
 * is written: a pipelined response can wait in the queue for as long as the client does not read.
 */
class CompressorSessionTest {
    private static final HttpCompressionStrategy STRATEGY = new HttpCompressionStrategy() {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean shouldCompress(HttpResponse response) {
            return true;
        }

        @Override
        public int getMaxZstdEncodeSize() {
            return 1 << 20;
        }
    };

    @Test
    void encoderIsCreatedWhenDataIsWritten() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        try {
            Compressor.Session session = prepare(channel);
            assertFalse(session.isEncoderCreated(), "the encoder was created before any data was written");

            session.push(Unpooled.copiedBuffer("hello ".repeat(100), StandardCharsets.UTF_8));
            session.finish();
            assertTrue(session.isEncoderCreated());
            ByteBuf compressed = session.poll();
            assertNotNull(compressed);
            compressed.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void discardingAnUnwrittenSessionCreatesNoEncoder() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        try {
            Compressor.Session session = prepare(channel);
            session.discard();
            assertFalse(session.isEncoderCreated());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static Compressor.Session prepare(EmbeddedChannel channel) {
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        Compressor.Session session = new Compressor(STRATEGY).prepare(ctx, request, response, 600);
        assertNotNull(session);
        return session;
    }
}
