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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameListenerDecorator;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * The idle check of {@link Http2ServerHandler} for pipelines built around netty's
 * {@link Http2FrameCodec} (the legacy multiplex handlers). PING, SETTINGS and WINDOW_UPDATE
 * frames reset the {@link IdleStateHandler}, so a peer could otherwise keep a connection whose
 * streams make no progress open forever. This handler closes the connection when no request or
 * response HEADERS or DATA were exchanged, and no response data was sent, for the idle timeout.
 * <p>
 * It is added between the codec and the {@link io.netty.handler.codec.http2.Http2MultiplexHandler},
 * where it sees the frames of all streams.
 *
 * @since 4.10.35
 */
@Internal
public final class Http2LegacyIdleHandler extends ChannelDuplexHandler {
    private final Http2FrameCodec codec;
    private final Http2OutputProgress outputProgress = new Http2OutputProgress();
    private long idleTimeoutNanos = -1;
    private long lastStreamActivity = System.nanoTime();

    /**
     * @param codec The frame codec of the connection
     */
    public Http2LegacyIdleHandler(Http2FrameCodec codec) {
        this.codec = codec;
        // the codec does not forward WINDOW_UPDATEs for the connection (stream 0)
        codec.decoder().frameListener(new Http2FrameListenerDecorator(codec.decoder().frameListener()) {
            @Override
            public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
                if (streamId == Http2CodecUtil.CONNECTION_STREAM_ID) {
                    outputProgress.onConnectionWindowUpdate(windowSizeIncrement);
                }
                super.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
            }
        });
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        IdleStateHandler idleStateHandler = ctx.pipeline().get(IdleStateHandler.class);
        if (idleStateHandler != null && idleStateHandler.getAllIdleTimeInMillis() > 0) {
            idleTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(idleStateHandler.getAllIdleTimeInMillis());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2HeadersFrame || msg instanceof Http2DataFrame) {
            lastStreamActivity = System.nanoTime();
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Http2HeadersFrame || msg instanceof Http2DataFrame) {
            lastStreamActivity = System.nanoTime();
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
        if (idleTimeoutNanos > 0) {
            long now = System.nanoTime();
            if (outputProgress.progressed(ctx.channel(), codec)) {
                // a response is still being drained to the peer
                lastStreamActivity = now;
            } else if (now - lastStreamActivity >= idleTimeoutNanos) {
                // only PING, SETTINGS and the like for the whole idle timeout
                ctx.close();
            }
        }
    }
}
