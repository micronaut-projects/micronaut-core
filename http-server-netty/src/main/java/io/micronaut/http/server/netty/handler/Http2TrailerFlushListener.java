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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameListenerDecorator;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;

/**
 * {@link Http2FrameListener} decorator that handles END_STREAM on trailing HEADERS frames.
 *
 * <p>When HTTP/2 trailing headers carry END_STREAM (common with Envoy, proxies, etc.), the
 * {@link io.netty.handler.codec.http2.DelegatingDecompressorFrameListener} never receives an
 * {@code onDataRead(..., endOfStream=true)} call, so it never flushes its decompressor tail.
 * This decorator intercepts trailing HEADERS with END_STREAM and routes the signal through
 * {@code onDataRead} on the wrapped listener, ensuring the decompressor is flushed before the
 * request body is completed.</p>
 *
 * @since 4.6.9
 * @author Micronaut
 */
@Internal
final class Http2TrailerFlushListener extends Http2FrameListenerDecorator {
    private final Http2FrameListener delegate;
    private final Http2Connection connection;
    private final Http2Connection.PropertyKey seenKey;

    Http2TrailerFlushListener(Http2FrameListener listener, Http2Connection connection) {
        super(listener);
        this.delegate = listener;
        this.connection = connection;
        this.seenKey = connection.newKey();
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endStream) throws Http2Exception {
        if (handleTrailerEndOfStream(ctx, streamId, endStream)) {
            return;
        }
        super.onHeadersRead(ctx, streamId, headers, padding, endStream);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        if (handleTrailerEndOfStream(ctx, streamId, endStream)) {
            return;
        }
        super.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
    }

    /**
     * Detect trailing HEADERS with END_STREAM and route the signal through the data path.
     *
     * @return {@code true} if this was a trailing HEADERS frame and has been handled
     */
    private boolean handleTrailerEndOfStream(ChannelHandlerContext ctx, int streamId, boolean endStream) throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        if (stream != null && stream.getProperty(seenKey) != null) {
            // Trailing headers: route END_STREAM through onDataRead so that
            // DelegatingDecompressorFrameListener flushes any pending decompressed bytes.
            if (endStream) {
                delegate.onDataRead(ctx, streamId, Unpooled.EMPTY_BUFFER, 0, true);
            }
            return true;
        }
        if (stream != null) {
            stream.setProperty(seenKey, Boolean.TRUE);
        }
        return false;
    }
}
