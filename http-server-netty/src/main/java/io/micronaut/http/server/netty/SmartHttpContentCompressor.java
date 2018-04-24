/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.http.server.netty;

import io.micronaut.http.MediaType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An extension of {@link HttpContentCompressor} that skips encoding if the content type is not compressible or if
 * the content is too small.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class SmartHttpContentCompressor extends HttpContentCompressor {

    private static final int LENGTH_1KB = 1024;
    private boolean skipEncoding = false;

    /**
     * Determines if encoding should occur based on the content type and length.
     *
     * @param contentType   The content type
     * @param contentLength The content length
     * @return True if the content is compressible and larger than 1KB
     */
    public static boolean shouldSkip(@Nullable String contentType, @Nullable Integer contentLength) {
        if (contentType == null) {
            return true;
        }
        return !MediaType.isTextBased(contentType) || (contentLength != null && contentLength >= 0 && contentLength < LENGTH_1KB);
    }

    /**
     * Determines if encoding should occur based on the content type and length.
     *
     * @param headers The headers that contain the content type and length
     * @return True if the content is compressible and larger than 1KB
     */
    public static boolean shouldSkip(HttpHeaders headers) {
        return shouldSkip(headers.get(HttpHeaderNames.CONTENT_TYPE), headers.getInt(HttpHeaderNames.CONTENT_LENGTH));
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        if (msg instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) msg;
            HttpHeaders headers = res.headers();
            skipEncoding = shouldSkip(headers);
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
