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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.quic.QuicStreamChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ends the QUIC stream of an HTTP/3 request when the request cannot be processed further. A
 * failure on the stream (e.g. a malformed request head, or a failed write) resets the stream,
 * which tears down the pipeline and so cancels the
 * response. Closing the stream before the request was read completely stops the peer from
 * sending the rest of it, so that the stream releases its slot and flow control credit.
 */
@Internal
final class Http3RequestStreamLifecycleHandler extends ChannelDuplexHandler {
    /**
     * {@code H3_NO_ERROR}, used to stop the rest of the request once the response is complete
     * (RFC 9114, section 4.1).
     */
    private static final int H3_NO_ERROR = 0x100;
    /**
     * {@code H3_REQUEST_CANCELLED}, used to reset a stream that failed.
     */
    private static final int H3_REQUEST_CANCELLED = 0x10c;

    private static final Logger LOG = LoggerFactory.getLogger(Http3RequestStreamLifecycleHandler.class);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (LOG.isDebugEnabled()) {
            // the message may contain request data, e.g. a malformed :path
            LOG.debug("Resetting HTTP/3 stream after a failure: {}", escape(String.valueOf(cause.getMessage())));
        }
        QuicStreamChannel stream = (QuicStreamChannel) ctx.channel();
        if (stream.isActive()) {
            stream.shutdown(H3_REQUEST_CANCELLED);
        }
        ctx.close();
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        QuicStreamChannel stream = (QuicStreamChannel) ctx.channel();
        if (stream.isActive() && !stream.isInputShutdown()) {
            stream.shutdownInput(H3_NO_ERROR);
        }
        ctx.close(promise);
    }

    private static String escape(String s) {
        return s.replace("\r", "\\r").replace("\n", "\\n");
    }
}
