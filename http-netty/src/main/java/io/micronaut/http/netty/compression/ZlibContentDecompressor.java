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
package io.micronaut.http.netty.compression;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.jspecify.annotations.Nullable;

/**
 * Retains Netty HTTP framing while replacing streaming codec construction.
 */
@Internal
public class ZlibContentDecompressor extends HttpContentDecompressor {
    private final NettyZlibCodecFactory factory;

    /**
     * @param factory The streaming codec factory
     */
    public ZlibContentDecompressor(NettyZlibCodecFactory factory) {
        super(false, 0);
        this.factory = factory;
    }

    @Override
    protected @Nullable EmbeddedChannel newContentDecoder(String contentEncoding) throws Exception {
        ChannelHandler decoder;
        if (HttpHeaderValues.GZIP.contentEqualsIgnoreCase(contentEncoding) || HttpHeaderValues.X_GZIP.contentEqualsIgnoreCase(contentEncoding)) {
            decoder = factory.newZlibDecoder(ZlibWrapper.GZIP, 0);
        } else if (HttpHeaderValues.DEFLATE.contentEqualsIgnoreCase(contentEncoding) || HttpHeaderValues.X_DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
            decoder = factory.newZlibDecoder(ZlibWrapper.ZLIB_OR_NONE, 0);
        } else if (HttpHeaderValues.BR.contentEqualsIgnoreCase(contentEncoding) && factory.isBrotliAvailable()) {
            decoder = factory.newBrotliDecoder(0);
        } else if (HttpHeaderValues.ZSTD.contentEqualsIgnoreCase(contentEncoding) && factory.isZstdAvailable()) {
            decoder = factory.newZstdDecoder(0);
        } else if (HttpHeaderValues.SNAPPY.contentEqualsIgnoreCase(contentEncoding)) {
            decoder = factory.newSnappyFrameDecoder();
        } else {
            return null;
        }
        return new EmbeddedChannel(ctx.channel().id(), ctx.channel().metadata().hasDisconnect(), ctx.channel().config(), decoder);
    }
}
