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
import io.micronaut.http.server.netty.handler.accesslog.element.AccessLogFormatParser;
import io.micronaut.http.server.netty.handler.accesslog.element.ConnectionMetadata;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http2.Http2Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

/**
 * This class manages the shared state between {@link Http2AccessLogConnectionEncoder} and
 * {@link Http2AccessLogFrameListener}.
 *
 * @since 4.4.2
 * @author Jonas Konrad
 */
@Internal
public final class Http2AccessLogManager {
    final Http2Connection connection;
    final Http2Connection.PropertyKey accessLogKey;
    final AccessLogFormatParser formatParser;
    final Logger logger;
    final Predicate<String> uriInclusion;

    AccessLog logForReuse;

    public Http2AccessLogManager(Factory factory, Http2Connection connection) {
        this.connection = connection;
        this.accessLogKey = connection.newKey();
        this.formatParser = new AccessLogFormatParser(factory.spec);
        this.logger = factory.logger == null ? LoggerFactory.getLogger(HttpAccessLogHandler.HTTP_ACCESS_LOGGER) : factory.logger;
        this.uriInclusion = factory.uriInclusion;
    }

    public void logHeaders(ChannelHandlerContext ctx, int streamId, HttpRequest request) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        if (uriInclusion != null && !uriInclusion.test(request.uri())) {
            return;
        }

        AccessLog accessLog;
        if (logForReuse != null) {
            accessLog = logForReuse;
            logForReuse = null;
        } else {
            accessLog = formatParser.newAccessLogger();
        }
        connection.stream(streamId).setProperty(accessLogKey, accessLog);
        accessLog.onRequestHeaders(ConnectionMetadata.ofNettyChannel(ctx.channel()), request.method().name(), request.headers(), request.uri(), HttpAccessLogHandler.H2_PROTOCOL_NAME);
    }

    /**
     * The factory.
     *
     * @param logger the logger
     * @param spec the pec
     * @param uriInclusion the uri inclusion
     */
    public record Factory(
        Logger logger,
        String spec,
        Predicate<String> uriInclusion
    ) {
    }
}
