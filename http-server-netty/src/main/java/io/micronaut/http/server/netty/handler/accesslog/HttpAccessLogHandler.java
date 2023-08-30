/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.server.netty.handler.accesslog.element.AccessLog;
import io.micronaut.http.server.netty.handler.accesslog.element.AccessLogFormatParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Predicate;

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

    private static final AttributeKey<AccessLogHolder> ACCESS_LOGGER = AttributeKey.valueOf("ACCESS_LOGGER");
    private static final String H2_PROTOCOL_NAME = "HTTP/2.0";

    private final Logger logger;
    private final AccessLogFormatParser accessLogFormatParser;
    private final Predicate<String> uriInclusion;

    /**
     * Creates a HttpAccessLogHandler.
     *
     * @param loggerName A logger name.
     * @param spec The log format specification.
     */
    public HttpAccessLogHandler(String loggerName, String spec) {
        this(loggerName == null || loggerName.isEmpty() ? null : LoggerFactory.getLogger(loggerName), spec, null);
    }

    /**
     * Creates a HttpAccessLogHandler.
     *
     * @param loggerName A logger name.
     * @param spec The log format specification.
     * @param uriInclusion A filtering Predicate that will be checked per URI.
     */
    public HttpAccessLogHandler(String loggerName, String spec, Predicate<String> uriInclusion) {
        this(loggerName == null || loggerName.isEmpty() ? null : LoggerFactory.getLogger(loggerName), spec, uriInclusion);
    }

    /**
     * Creates a HttpAccessLogHandler.
     *
     * @param logger A logger. Will log at info level.
     * @param spec The log format specification.
     */
    public HttpAccessLogHandler(Logger logger, String spec) {
        this(logger, spec, null);
    }

    /**
     * Creates a HttpAccessLogHandler.
     *
     * @param logger A logger. Will log at info level.
     * @param spec The log format specification.
     * @param uriInclusion A filtering Predicate that will be checked per URI.
     */
    public HttpAccessLogHandler(Logger logger, String spec, Predicate<String> uriInclusion) {
        super();
        this.logger = logger == null ? LoggerFactory.getLogger(HTTP_ACCESS_LOGGER) : logger;
        this.accessLogFormatParser = new AccessLogFormatParser(spec);
        this.uriInclusion = uriInclusion;
    }

    private SocketChannel findSocketChannel(Channel channel) {
        if (channel instanceof SocketChannel socketChannel) {
            return socketChannel;
        }
        Channel parent = channel.parent();
        if (parent == null) {
            throw new IllegalArgumentException("No socket channel available");
        }
        return findSocketChannel(parent);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Http2Exception {
        if (logger.isInfoEnabled() && msg instanceof HttpRequest request) {
            final SocketChannel channel = findSocketChannel(ctx.channel());
            AccessLogHolder accessLogHolder = getAccessLogHolder(ctx, true);
            assert accessLogHolder != null; // can only return null when createIfMissing is false
            if (uriInclusion == null || uriInclusion.test(request.uri())) {
                final HttpHeaders headers = request.headers();
                // Trying to detect http/2
                String protocol;
                if (headers.contains(ExtensionHeaderNames.STREAM_ID.text()) || headers.contains(ExtensionHeaderNames.SCHEME.text())) {
                    protocol = H2_PROTOCOL_NAME;
                } else {
                    protocol = request.protocolVersion().text();
                }
                accessLogHolder.createLogForRequest().onRequestHeaders(channel, request.method().name(), request.headers(), request.uri(), protocol);
            } else {
                accessLogHolder.excludeRequest();
            }
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

    private void log(ChannelHandlerContext ctx, Object msg, ChannelPromise promise, AccessLog accessLog) {
        ctx.write(msg, promise.unvoid()).addListener(future -> {
            if (future.isSuccess()) {
                accessLog.log(logger);
            }
        });
    }

    private void processWriteEvent(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        AccessLogHolder accessLogHolder = getAccessLogHolder(ctx, false);
        if (accessLogHolder != null) {
            boolean isContinueResponse = msg instanceof HttpResponse hr && hr.status().equals(HttpResponseStatus.CONTINUE);
            AccessLog accessLogger = accessLogHolder.getLogForResponse(
                    msg instanceof LastHttpContent && !isContinueResponse);
            if (accessLogger != null && !isContinueResponse) {
                if (msg instanceof HttpResponse response) {
                    accessLogger.onResponseHeaders(ctx, response.headers(), response.status().codeAsText().toString());
                }
                if (msg instanceof LastHttpContent content) {
                    accessLogger.onLastResponseWrite(content.content().readableBytes());
                    log(ctx, msg, promise, accessLogger);
                    return;
                } else if (msg instanceof ByteBufHolder holder) {
                    accessLogger.onResponseWrite(holder.content().readableBytes());
                } else if (msg instanceof ByteBuf buf) {
                    accessLogger.onResponseWrite(buf.readableBytes());
                }
            }
        }
        super.write(ctx, msg, promise);
    }

    @Nullable
    private AccessLogHolder getAccessLogHolder(ChannelHandlerContext ctx, boolean createIfMissing) {
        final Attribute<AccessLogHolder> attr = ctx.channel().attr(ACCESS_LOGGER);
        AccessLogHolder holder = attr.get();
        if (holder == null) {
            if (!createIfMissing) {
                return null;
            }
            holder = new AccessLogHolder();
            attr.set(holder);
        }
        return holder;
    }

    /**
     * Holder for {@link AccessLog} instances. {@link AccessLog} can only handle one concurrent request at a time, this
     * class multiplexes access where necessary.
     */
    private final class AccessLogHolder {
        private final Queue<AccessLog> liveLogs = new LinkedList<>(); // ArrayDeque doesn't like null elements :(
        private AccessLog logForReuse;

        AccessLog createLogForRequest() {
            AccessLog log = logForReuse;
            logForReuse = null;
            if (log != null) {
                log.reset();
            } else {
                log = accessLogFormatParser.newAccessLogger();
            }
            liveLogs.add(log);
            return log;
        }

        void excludeRequest() {
            liveLogs.add(null);
        }

        @Nullable
        AccessLog getLogForResponse(boolean finishResponse) {
            if (finishResponse) {
                AccessLog accessLog = liveLogs.poll();
                logForReuse = accessLog;
                return accessLog;
            } else {
                return liveLogs.peek();
            }
        }
    }
}
