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

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Inflates a gzip or deflate request body, and tells whether the body ended before its
 * compressed stream did. The netty zlib decoders do not fail when the input ends, so a stream
 * that stops in the middle, or without its gzip trailer, would otherwise be read as a complete,
 * shorter body. The members of a concatenated gzip stream are inflated one by one, because the
 * netty decoder that continues with the next member by itself never reports the end of a member.
 *
 * @author Denis Stepanov
 * @since 5.2.21
 */
@Internal
final class ZlibBodyDecoder extends ByteToMessageDecoder {
    private final ZlibWrapper wrapper;
    private final int maxAllocation;
    @Nullable
    private Stream stream;

    /**
     * @param wrapper       The wrapper of the compressed stream
     * @param maxAllocation The maximum size of the decompression buffer, or 0 for no limit
     */
    ZlibBodyDecoder(ZlibWrapper wrapper, int maxAllocation) {
        this.wrapper = wrapper;
        this.maxAllocation = maxAllocation;
    }

    /**
     * @return Whether the input so far ends inside a compressed stream. A body without any bytes
     * has no stream to end.
     */
    boolean isTruncated() {
        return stream != null && !stream.isClosed();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (stream == null || (wrapper == ZlibWrapper.GZIP && stream.isClosed())) {
            // the next gzip member: a closed decoder skips its input
            end(ctx);
            stream = new Stream(wrapper, maxAllocation);
        }
        stream.inflate(ctx, in, out);
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        end(ctx);
    }

    private void end(ChannelHandlerContext ctx) throws Exception {
        if (stream != null) {
            stream.end(ctx);
        }
    }

    /**
     * The decoder of one deflate stream, or of one member of a gzip stream.
     */
    private static final class Stream extends JdkZlibDecoder {
        Stream(ZlibWrapper wrapper, int maxAllocation) {
            super(wrapper, false, maxAllocation);
        }

        void inflate(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            decode(ctx, in, out);
        }

        void end(ChannelHandlerContext ctx) throws Exception {
            // releases the inflater
            handlerRemoved0(ctx);
        }
    }
}
