/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.netty.handler.accesslog;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameListenerDecorator;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.HttpConversionUtil;

/**
 * Special {@link Http2FrameListener} that logs the request data.
 *
 * @author Jonas Konrad
 * @since 4.4.2
 */
@Internal
public final class Http2AccessLogFrameListener extends Http2FrameListenerDecorator {
    private final Http2AccessLogManager manager;

    public Http2AccessLogFrameListener(Http2FrameListener listener, Http2AccessLogManager manager) {
        super(listener);
        this.manager = manager;
    }

    private void logHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers) throws Http2Exception {
        HttpRequest request = HttpConversionUtil.toHttpRequest(streamId, headers, false);
        manager.logHeaders(ctx, streamId, request);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endStream) throws Http2Exception {
        logHeaders(ctx, streamId, headers);
        super.onHeadersRead(ctx, streamId, headers, padding, endStream);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
        logHeaders(ctx, streamId, headers);
        super.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
    }
}
