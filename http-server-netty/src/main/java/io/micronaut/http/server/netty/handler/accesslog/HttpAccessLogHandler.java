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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
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

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Http2Exception {
        if (logger.isInfoEnabled() && msg instanceof HttpRequest) {
            final SocketChannel channel = (SocketChannel) ctx.channel();
            final HttpRequest request = (HttpRequest) msg;
            AccessLogHolder accessLogHolder = getAccessLogHolder(ctx, true);
            if (uriInclusion == null || uriInclusion.test(request.uri())) {
                final HttpHeaders headers = request.headers();
                // Trying to detect http/2
                String protocol;
                if (headers.contains(ExtensionHeaderNames.STREAM_ID.text()) || headers.contains(ExtensionHeaderNames.SCHEME.text())) {
                    protocol = H2_PROTOCOL_NAME;
                } else {
                    protocol = request.protocolVersion().text();
                }
                accessLogHolder.createLogForRequest(request).onRequestHeaders(channel, request.method().name(), request.headers(), request.uri(), protocol);
            } else {
                accessLogHolder.excludeRequest(request);
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
            boolean isContinueResponse = msg instanceof HttpResponse && ((HttpResponse) msg).status().equals(HttpResponseStatus.CONTINUE);
            AccessLog accessLogger = accessLogHolder.getLogForResponse(
                    msg instanceof HttpMessage ? (HttpMessage) msg : null,
                    msg instanceof LastHttpContent && !isContinueResponse);
            if (accessLogger != null && !isContinueResponse) {
                if (msg instanceof HttpResponse) {
                    accessLogger.onResponseHeaders(ctx, ((HttpResponse) msg).headers(), ((HttpResponse) msg).status().codeAsText().toString());
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
        }
        super.write(ctx, msg, promise);
    }

    @Nullable
    @Contract("_, true -> !null") // can only return null when createIfMissing is false
    private AccessLogHolder getAccessLogHolder(ChannelHandlerContext ctx, boolean createIfMissing) {
        final Attribute<AccessLogHolder> attr = ctx.channel().attr(ACCESS_LOGGER);
        AccessLogHolder holder = attr.get();
        if (holder == null) {
            if (!createIfMissing) {
                return null;
            }
            attr.set(holder = new AccessLogHolder());
        }
        return holder;
    }

    /**
     * Holder for {@link AccessLog} instances. {@link AccessLog} can only handle one concurrent request at a time, this
     * class multiplexes access where necessary.
     */
    private final class AccessLogHolder {
        private final Map<Long, AccessLog> liveAccessLogsByStreamId = new HashMap<>();
        // HTTP1 does not have stream IDs. To emulate them, we have two counters. One counts up on every request, and
        // the other counts up on every *completed* response.
        private long http1NextRequestStreamId = 0;
        private long currentPendingResponseStreamId = 0;

        private AccessLog logForReuse;

        AccessLog createLogForRequest(HttpRequest request) {
            long streamId = getOrCreateStreamId(request);
            AccessLog log = logForReuse;
            logForReuse = null;
            if (log != null) {
                log.reset();
            } else {
                log = accessLogFormatParser.newAccessLogger();
            }
            liveAccessLogsByStreamId.put(streamId, log);
            return log;
        }

        void excludeRequest(HttpRequest request) {
            getOrCreateStreamId(request); // claim stream id, but no access logger
        }

        private long getOrCreateStreamId(HttpRequest request) {
            String streamIdHeader = request.headers().get(ExtensionHeaderNames.STREAM_ID.text());
            if (streamIdHeader == null) {
                return http1NextRequestStreamId++;
            } else {
                return Long.parseLong(streamIdHeader);
            }
        }

        @Nullable
        AccessLog getLogForResponse(@Nullable HttpMessage msg, boolean finishResponse) {
            String streamIdHeader = msg == null ? null : msg.headers().get(ExtensionHeaderNames.STREAM_ID.text());
            long streamId;
            if (streamIdHeader == null) {
                streamId = currentPendingResponseStreamId;
                if (finishResponse) {
                    currentPendingResponseStreamId++;
                }
            } else {
                currentPendingResponseStreamId = streamId = Long.parseLong(streamIdHeader);
            }
            if (finishResponse) {
                AccessLog accessLog = liveAccessLogsByStreamId.remove(streamId);
                logForReuse = accessLog;
                return accessLog;
            } else {
                return liveAccessLogsByStreamId.get(streamId);
            }
        }
    }
}
