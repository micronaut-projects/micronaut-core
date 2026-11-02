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
import io.micronaut.http.server.netty.handler.PipeliningServerHandler;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.quic.QuicStreamChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ends the QUIC stream of an HTTP/3 request when the request cannot be processed further. A
 * failure on the stream (e.g. a malformed request head, or a failed write) resets the stream,
 * which tears down the pipeline and so cancels the
 * response. Closing the stream, or completing the response of a request whose body is
 * discarded, before the request was read completely stops the peer from sending the rest of it,
 * so that the stream releases its slot and flow control credit.
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

    /**
     * Whether the final response was written. Event loop only.
     */
    private boolean responseComplete;
    /**
     * Whether the rest of the request body is dropped. Event loop only.
     */
    private boolean inputDiscarded;

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
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == PipeliningServerHandler.QUIC_INPUT_DISCARDED_EVENT) {
            inputDiscarded = true;
            if (responseComplete) {
                stopInput((QuicStreamChannel) ctx.channel());
            }
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof LastHttpContent && !isInformational(msg)) {
            // the response is complete. Once the request handler discards the rest of the
            // request body, if any, ask the peer to stop sending it. The app may still read it,
            // e.g. after answering 202, as HTTP/2 does.
            promise = promise.unvoid().addListener(future -> {
                if (future.isSuccess()) {
                    responseComplete = true;
                    if (inputDiscarded) {
                        stopInput((QuicStreamChannel) ctx.channel());
                    }
                }
            });
        }
        ctx.write(msg, promise);
    }

    private static boolean isInformational(Object msg) {
        return msg instanceof HttpResponse response && response.status().codeClass() == HttpStatusClass.INFORMATIONAL;
    }

    private static void stopInput(QuicStreamChannel stream) {
        if (stream.isActive() && !stream.isInputShutdown()) {
            stream.shutdownInput(H3_NO_ERROR);
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        stopInput((QuicStreamChannel) ctx.channel());
        ctx.close(promise);
    }

    private static String escape(String s) {
        return s.replace("\r", "\\r").replace("\n", "\\n");
    }
}
