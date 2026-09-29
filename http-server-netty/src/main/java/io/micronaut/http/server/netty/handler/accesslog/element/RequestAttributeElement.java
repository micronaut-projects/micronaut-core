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
package io.micronaut.http.server.netty.handler.accesslog.element;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.handler.accesslog.HttpAccessLogHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * RequestAttributeElement LogElement. The value of a Micronaut request attribute, read when the
 * response headers are written.
 *
 * @since 5.3.0
 */
final class RequestAttributeElement extends AbstractHttpMessageLogElement {

    private final String attribute;

    /**
     * Creates a RequestAttributeElement.
     *
     * @param attribute The request attribute name.
     */
    RequestAttributeElement(String attribute) {
        this.attribute = attribute;
        this.events = Event.RESPONSE_HEADERS_EVENTS;
    }

    @Override
    public String onResponseHeaders(ChannelHandlerContext ctx, HttpHeaders headers, String status) {
        Object responseRequest = ctx.channel().attr(HttpAccessLogHandler.RESPONSE_REQUEST).get();
        if (responseRequest instanceof NettyHttpRequest<?> nettyRequest) {
            // filters may have replaced the request that the route set its attributes on
            responseRequest = nettyRequest.getEffectiveRequest();
        }
        if (responseRequest instanceof HttpRequest<?> request) {
            Object value = request.getAttribute(attribute).orElse(null);
            if (value instanceof Optional<?> optional) {
                value = optional.orElse(null);
            }
            if (value != null) {
                try {
                    return escape(value.toString());
                } catch (RuntimeException e) {
                    // a failing toString must not fail the response write
                    return ConstantElement.UNKNOWN_VALUE;
                }
            }
        }
        return ConstantElement.UNKNOWN_VALUE;
    }

    /**
     * Escape a value for the log line. Unlike header values, which Netty validates, attribute
     * values are arbitrary, so control characters and line separators are escaped as well to
     * prevent log injection.
     *
     * @param value The value
     * @return The escaped value, or {@code -} if it is empty
     */
    static String escape(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return ConstantElement.UNKNOWN_VALUE;
        }
        StringBuilder buffer = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            String escaped = switch (c) {
                case '\b' -> "\\b";
                case '\n' -> "\\n";
                case '\r' -> "\\r";
                case '\t' -> "\\t";
                case '\\' -> "\\\\";
                case '"' -> "\\\"";
                default -> c < 0x20 || (c >= 0x7F && c <= 0x9F) || c == '\u2028' || c == '\u2029'
                    ? String.format("\\u%04x", (int) c) : null;
            };
            if (escaped != null) {
                if (buffer == null) {
                    buffer = new StringBuilder(value.length() + 8).append(value, 0, i);
                }
                buffer.append(escaped);
            } else if (buffer != null) {
                buffer.append(c);
            }
        }
        return buffer == null ? value : buffer.toString();
    }

    @Override
    protected String value(HttpHeaders headers) {
        return ConstantElement.UNKNOWN_VALUE;
    }

    @Override
    public String toString() {
        return "%{" + attribute + '}' + RequestLineElement.REQUEST_LINE;
    }
}
