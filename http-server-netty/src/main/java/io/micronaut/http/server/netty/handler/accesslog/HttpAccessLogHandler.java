/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.handler.accesslog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.http.server.netty.handler.accesslog.element.AccessLogFormatParser;
import io.micronaut.http.server.netty.handler.accesslog.element.AccessLog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

/**
 * Logging handler for HTTP access logs.
 * Access logs will be logged at info level.
 *
 * @author croudet
 * @since 2.0
 */
@Sharable
public class HttpAccessLogHandler extends ChannelDuplexHandler {
    /**
     * The default logger name.
     */
    public static final String HTTP_ACCESS_LOGGER = "HTTP_ACCESS_LOGGER";

    private static final AttributeKey<AccessLog> ACCESS_LOGGER = AttributeKey.valueOf("ACCESS_LOGGER");
    private static final String H2_PROTOCOL_NAME = "HTTP/2.0";

    private final Logger logger;
    private final AccessLogFormatParser accessLogFormatParser;

    /**
     * Creates a HttpAccessLogHandler.
     *
     * @param loggerName A logger name.
     * @param spec The log format specification.
     */
    public HttpAccessLogHandler(String loggerName, String spec) {
        this(loggerName == null || loggerName.isEmpty() ? null : LoggerFactory.getLogger(loggerName), spec);
    }

    /**
     * Creates a HttpAccessLogHandler.
     *
     * @param logger A logger. Will log at info level.
     * @param spec The log format specification.
     */
    public HttpAccessLogHandler(Logger logger, String spec) {
        super();
        this.logger = logger == null ? LoggerFactory.getLogger(HTTP_ACCESS_LOGGER) : logger;
        this.accessLogFormatParser = new AccessLogFormatParser(spec);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Http2Exception {
        if (logger.isInfoEnabled() && msg instanceof HttpRequest) {
            final SocketChannel channel = (SocketChannel) ctx.channel();
            final HttpRequest request = (HttpRequest) msg;
            final HttpHeaders headers = request.headers();
            // Trying to detect http/2
            String protocol;
            if (headers.contains(ExtensionHeaderNames.STREAM_ID.text()) || headers.contains(ExtensionHeaderNames.SCHEME.text())) {
                protocol = H2_PROTOCOL_NAME;
            } else {
                protocol = request.protocolVersion().text();
            }
            accessLog(channel).onRequestHeaders(channel, request.method().name(), request.headers(), request.uri(), protocol);
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (logger.isInfoEnabled()) {
            processWriteEvent(ctx, msg, promise);
        } else {
            super.write(ctx, msg, promise);
        }
    }

    private AccessLog accessLog(SocketChannel channel) {
        final Attribute<AccessLog> attr = channel.attr(ACCESS_LOGGER);
        AccessLog accessLog = attr.get();
        if (accessLog == null) {
            accessLog = accessLogFormatParser.newAccessLogger();
            attr.set(accessLog);
        } else {
            accessLog.reset();
        }
        return accessLog;
    }

    private void log(ChannelHandlerContext ctx, Object msg, ChannelPromise promise, AccessLog accessLog) {
        ctx.write(msg, promise.unvoid()).addListener(future -> {
            if (future.isSuccess()) {
                accessLog.log(logger);
            }
        });
    }

    private static boolean processHttpResponse(HttpResponse response, AccessLog accessLogger, ChannelHandlerContext ctx, ChannelPromise promise) {
        final HttpResponseStatus status = response.status();
        if (status.equals(HttpResponseStatus.CONTINUE)) {
            ctx.write(response, promise);
            return true;
        }
        accessLogger.onResponseHeaders(ctx, response.headers(), status.codeAsText().toString());
        return false;
    }

    private void processWriteEvent(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        final AccessLog accessLogger = ctx.channel().attr(ACCESS_LOGGER).get();
        if (accessLogger != null) {
            if (msg instanceof HttpResponse && processHttpResponse((HttpResponse) msg, accessLogger, ctx, promise)) {
                return;
            }
            if (msg instanceof LastHttpContent) {
                accessLogger.onLastResponseWrite(((LastHttpContent) msg).content().readableBytes());
                log(ctx, msg, promise, accessLogger);
                return;
            } else if (msg instanceof ByteBufHolder) {
                accessLogger.onResponseWrite(((ByteBufHolder) msg).content().readableBytes());
            } else if (msg instanceof ByteBuf) {
                accessLogger.onResponseWrite(((ByteBuf) msg).readableBytes());
            }
        }
        super.write(ctx, msg, promise);
    }
}
