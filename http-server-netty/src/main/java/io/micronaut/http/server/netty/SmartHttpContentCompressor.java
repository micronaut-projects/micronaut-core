/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;

import java.util.List;

/**
 * An extension of {@link HttpContentCompressor} that skips encoding if the content type is not compressible or if
 * the content is too small.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class SmartHttpContentCompressor extends HttpContentCompressor {

    private final HttpCompressionStrategy httpCompressionStrategy;
    private boolean skipEncoding = false;

    /**
     * Creates a SmartHttpContentCompressor with the given compression logic.
     *
     * @param httpCompressionStrategy The compression strategy
     */
    SmartHttpContentCompressor(HttpCompressionStrategy httpCompressionStrategy) {
        super(httpCompressionStrategy.getCompressionLevel());
        this.httpCompressionStrategy = httpCompressionStrategy;
    }

    /**
     * Determines if encoding should occur based on the response.
     *
     * @param response The response
     * @return True if the content should not be compressed
     */
    public boolean shouldSkip(HttpResponse response) {
        return !httpCompressionStrategy.shouldCompress(response);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        if (msg instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) msg;
            skipEncoding = shouldSkip(res);
        }
        super.encode(ctx, msg, out);
    }

    @Override
    protected Result beginEncode(HttpResponse headers, String acceptEncoding) throws Exception {
        if (skipEncoding) {
            return null;
        }
        return super.beginEncode(headers, acceptEncoding);
    }
}
