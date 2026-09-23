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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.DefaultHttp2LocalFlowController;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2LocalFlowController;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import org.jspecify.annotations.Nullable;

/**
 * Raises the receive window of the HTTP/2 connection as a whole (stream 0) once the connection
 * preface has been sent. {@code SETTINGS_INITIAL_WINDOW_SIZE} only sets the window of each
 * stream; the connection window starts at {@link Http2CodecUtil#DEFAULT_WINDOW_SIZE} and can only
 * be raised with a {@code WINDOW_UPDATE} on stream 0.
 * <p>
 * The {@link Http2ServerHandler} does this itself. This handler is for pipelines built around
 * netty's {@link io.netty.handler.codec.http2.Http2FrameCodec}, which cannot be extended: it is
 * added right after the codec, raises the window and then removes itself.
 *
 * @since 5.2.2
 * @author Graeme Rocher
 */
@Internal
public final class Http2ConnectionWindow extends ChannelInboundHandlerAdapter {
    private final Http2ConnectionHandler connectionHandler;
    private final int windowSize;

    /**
     * @param connectionHandler The connection handler whose connection window to raise
     * @param windowSize        The target window size, see {@link #effectiveWindowSize(Http2Settings, Integer)}
     */
    public Http2ConnectionWindow(Http2ConnectionHandler connectionHandler, int windowSize) {
        this.connectionHandler = connectionHandler;
        this.windowSize = windowSize;
    }

    /**
     * Compute the connection window size to use for the given settings: the larger of the
     * configured value and the window derived from the stream window. The derived window
     * follows the rule of netty's {@link io.netty.handler.codec.http2.Http2FrameCodec}, which
     * raises the connection window by twice the amount the stream window exceeds the protocol
     * default, so that a single stream cannot use up the whole connection window. The codec
     * applies that rule on its own, so a configured value cannot go below it in the legacy
     * pipeline; using the same floor here keeps both pipelines consistent.
     *
     * @param initialSettings The local settings sent in the preface
     * @param configured      The explicitly configured connection window size, if any
     * @return The connection window size, at least {@link Http2CodecUtil#DEFAULT_WINDOW_SIZE}
     */
    public static int effectiveWindowSize(Http2Settings initialSettings, @Nullable Integer configured) {
        long derived = Http2CodecUtil.DEFAULT_WINDOW_SIZE;
        Integer streamWindowSize = initialSettings.initialWindowSize();
        if (streamWindowSize != null && streamWindowSize > Http2CodecUtil.DEFAULT_WINDOW_SIZE) {
            derived += 2L * (streamWindowSize - Http2CodecUtil.DEFAULT_WINDOW_SIZE);
        }
        if (configured != null) {
            derived = Math.max(derived, configured);
        }
        return (int) Math.min(Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE, derived);
    }

    /**
     * Raise the connection window of the given connection to {@code windowSize} if it is
     * smaller, and advertise the increase to the peer right away. Must be called on the event
     * loop after the preface has been sent, so that the {@code WINDOW_UPDATE} follows the
     * {@code SETTINGS} frame.
     *
     * @param ctx        The context of the connection handler
     * @param connection The connection
     * @param windowSize The target window size
     * @throws Http2Exception If the flow controller rejects the update
     */
    public static void raise(ChannelHandlerContext ctx, Http2Connection connection, int windowSize) throws Http2Exception {
        Http2Stream connectionStream = connection.connectionStream();
        Http2LocalFlowController flowController = connection.local().flowController();
        int delta = windowSize - flowController.initialWindowSize(connectionStream);
        boolean changed = false;
        if (delta > 0) {
            // this raises the size the window is refilled to, and writes the WINDOW_UPDATE only
            // when the unconsumed part of the current window has dropped below the update ratio
            // (half by default). That is always the case when the window at least doubles.
            flowController.incrementWindowSize(connectionStream, delta);
            changed = true;
        }
        // the refill size may also have been raised before, by netty's Http2FrameCodec
        if (flowController.windowSize(connectionStream) < flowController.initialWindowSize(connectionStream) && flowController instanceof DefaultHttp2LocalFlowController defaultFlowController) {
            // smaller increase: the update would only be sent once the peer has used part of
            // the old window. Force it now by briefly moving the update threshold to the top
            // of the window, so that the configured size applies from the first request on.
            float ratio = defaultFlowController.windowUpdateRatio(connectionStream);
            defaultFlowController.windowUpdateRatio(connectionStream, Math.nextDown(1.0f));
            defaultFlowController.windowUpdateRatio(connectionStream, ratio);
            changed = true;
        }
        if (changed) {
            ctx.flush();
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            raiseAndRemove(ctx);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        raiseAndRemove(ctx);
        ctx.fireChannelActive();
    }

    private void raiseAndRemove(ChannelHandlerContext ctx) throws Http2Exception {
        raise(ctx.pipeline().context(connectionHandler), connectionHandler.connection(), windowSize);
        ctx.pipeline().remove(this);
    }
}
