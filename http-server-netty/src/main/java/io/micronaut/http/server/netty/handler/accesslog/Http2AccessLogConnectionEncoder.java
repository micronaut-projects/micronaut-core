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
import io.micronaut.http.server.netty.handler.accesslog.element.AccessLog;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DecoratingHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;

/**
 * Special {@link Http2ConnectionEncoder} that logs the response data.
 *
 * @author Jonas Konrad
 * @since 4.4.2
 */
@Internal
public final class Http2AccessLogConnectionEncoder extends DecoratingHttp2ConnectionEncoder {
    private final Http2AccessLogManager manager;

    public Http2AccessLogConnectionEncoder(Http2ConnectionEncoder delegate, Http2AccessLogManager manager) {
        super(delegate);
        this.manager = manager;
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endStream, ChannelPromise promise) {
        promise = writeHeaders0(ctx, streamId, headers, endStream, promise);
        return super.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream, ChannelPromise promise) {
        promise = writeHeaders0(ctx, streamId, headers, endStream, promise);
        return super.writeHeaders(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream, promise);
    }

    private ChannelPromise writeHeaders0(ChannelHandlerContext ctx, int streamId, Http2Headers headers, boolean endStream, ChannelPromise promise) {
        if (AsciiString.contentEquals(headers.status(), HttpResponseStatus.CONTINUE.codeAsText())) {
            return promise;
        }
        AccessLog accessLog = manager.connection.stream(streamId).getProperty(manager.accessLogKey);
        if (accessLog == null) {
            return promise;
        }
        HttpResponse response;
        try {
            response = HttpConversionUtil.toHttpResponse(streamId, headers, false);
        } catch (Http2Exception e) {
            throw new RuntimeException(e);
        }
        accessLog.onResponseHeaders(ctx, response.headers(), response.status().codeAsText().toString());
        if (endStream) {
            accessLog.onLastResponseWrite(0);
            promise = promise.unvoid();
            finish(accessLog, promise);
        }
        return promise;
    }

    @Override
    public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endStream, ChannelPromise promise) {
        AccessLog accessLog = manager.connection.stream(streamId).getProperty(manager.accessLogKey);
        if (accessLog != null) {
            if (endStream) {
                accessLog.onLastResponseWrite(data.readableBytes());
                promise = promise.unvoid();
                finish(accessLog, promise);
            } else {
                accessLog.onResponseWrite(data.readableBytes());
            }
        }

        return super.writeData(ctx, streamId, data, padding, endStream, promise);
    }

    private void finish(AccessLog accessLog, ChannelPromise promise) {
        promise.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                accessLog.log(manager.logger);
                manager.logForReuse = accessLog;
            }
        });
    }
}
