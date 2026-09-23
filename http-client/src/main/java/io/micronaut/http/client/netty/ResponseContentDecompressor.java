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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;

import java.util.List;

/**
 * {@link HttpContentDecompressor} that leaves responses without a body untouched. Netty would
 * otherwise "decode" the empty body and strip the {@code Content-Encoding} header the server sent.
 *
 * @since 5.3.0
 */
@Internal
@SuppressWarnings("java:S110") // the hierarchy depth comes from Netty
final class ResponseContentDecompressor extends HttpContentDecompressor {
    private boolean bodiless;

    ResponseContentDecompressor() {
        super(false, 0);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        if (msg instanceof HttpMessage message) {
            bodiless = isBodiless(message);
        }
        super.decode(ctx, msg, out);
    }

    @Override
    protected @Nullable EmbeddedChannel newContentDecoder(String contentEncoding) throws Exception {
        if (bodiless) {
            // no content to decode, pass the message through with its headers
            return null;
        }
        return super.newContentDecoder(contentEncoding);
    }

    private static boolean isBodiless(HttpMessage message) {
        if (message instanceof HttpResponse response) {
            int code = response.status().code();
            if (code == HttpResponseStatus.NO_CONTENT.code() || code == HttpResponseStatus.NOT_MODIFIED.code()) {
                return true;
            }
        }
        if (message instanceof FullHttpMessage full) {
            return !full.content().isReadable();
        }
        return HttpUtil.getContentLength(message, -1L) == 0;
    }
}
