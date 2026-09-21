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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;

/**
 * {@link HttpContentDecompressor} that leaves the response of a request alone when the request
 * channel has the {@link #SKIP_DECOMPRESSION} attribute, so that encoded bytes can be relayed
 * unchanged (see {@link io.micronaut.http.client.RawRequestOptions#isDecompress()}).
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class MicronautHttpContentDecompressor extends HttpContentDecompressor {
    /**
     * Set on the request channel (the connection for HTTP/1, the stream for HTTP/2 and HTTP/3)
     * while a response must not be decompressed.
     */
    static final AttributeKey<Boolean> SKIP_DECOMPRESSION = AttributeKey.valueOf(MicronautHttpContentDecompressor.class, "skip-decompression");

    @Override
    protected @Nullable EmbeddedChannel newContentDecoder(String contentEncoding) throws Exception {
        if (Boolean.TRUE.equals(ctx.channel().attr(SKIP_DECOMPRESSION).get())) {
            return null;
        }
        return super.newContentDecoder(contentEncoding);
    }
}
