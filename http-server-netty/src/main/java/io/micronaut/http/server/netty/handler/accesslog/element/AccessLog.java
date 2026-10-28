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
package io.micronaut.http.server.netty.handler.accesslog.element;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.server.netty.handler.accesslog.element.AccessLogFormatParser.IndexedLogElement;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;
import org.slf4j.Logger;

import java.util.List;

/**
 * An Access log instance.
 *
 * @author croudet
 * @since 2.0
 */
public class AccessLog {
    private final List<IndexedLogElement> onRequestHeadersElements;
    private final List<IndexedLogElement> onResponseHeadersElements;
    private final List<IndexedLogElement> onResponseWriteElements;
    private final List<IndexedLogElement> onLastResponseWriteElements;
    private final String[] elements;

    /**
     * Creates an AccessLog.
     *
     * @param onRequestHeadersElements The LogElements that depends on the ON_REQUEST_HEADERS event.
     * @param onResponseHeadersElements The LogElements that depends on the ON_RESPONSE_HEADERS event.
     * @param onResponseWriteElements The LogElements that depends on the ON_WRITE_RESPONSE event.
     * @param onLastResponseWriteElements The LogElements that depends on the ON_LAST_WRITE_RESPONSE event.
     * @param elements The array of values.
     */
    AccessLog(List<IndexedLogElement> onRequestHeadersElements, List<IndexedLogElement> onResponseHeadersElements, List<IndexedLogElement> onResponseWriteElements, List<IndexedLogElement> onLastResponseWriteElements, String[] elements) {
        this.onRequestHeadersElements = onRequestHeadersElements;
        this.onResponseHeadersElements = onResponseHeadersElements;
        this.onResponseWriteElements = onResponseWriteElements;
        this.onLastResponseWriteElements = onLastResponseWriteElements;
        this.elements = elements;
    }

    /**
     * Resets the current values.
     */
    public void reset() {
        onRequestHeadersElements.forEach(this::resetIndexedLogElement);
        onResponseHeadersElements.forEach(this::resetIndexedLogElement);
        onResponseWriteElements.forEach(this::resetIndexedLogElement);
        onLastResponseWriteElements.forEach(this::resetIndexedLogElement);
    }

    /**
     * Triggers LogElements for the ON_REQUEST_HEADERS event.
     *
     * @param metadata The connection metadata.
     * @param method The http method.
     * @param headers The request headers.
     * @param uri The uri.
     * @param protocol The protocol.
     */
    public void onRequestHeaders(ConnectionMetadata metadata, String method, HttpHeaders headers, String uri, String protocol) {
        for (IndexedLogElement element: onRequestHeadersElements) {
            elements[element.index] = escape(element.onRequestHeaders(metadata, method, headers, uri, protocol));
        }
    }

   /**
    * Triggers LogElements for the ON_REQUEST_HEADERS event.
    *
    * @param channel The socket channel.
    * @param method The http method.
    * @param headers The request headers.
    * @param uri The uri.
    * @param protocol The protocol.
    * @deprecated Use {@link #onRequestHeaders(ConnectionMetadata, String, HttpHeaders, String, String)} instead
    */
    @Deprecated
    public void onRequestHeaders(SocketChannel channel, String method, HttpHeaders headers, String uri, String protocol) {
        onRequestHeaders(new ConnectionMetadataImpl.SocketChannelMetadata(channel), method, headers, uri, protocol);
    }

    /**
     * Triggers LogElements for the ON_RESPONSE_HEADERS event.
     *
     * @param ctx The ChannelHandlerContext.
     * @param headers The response headers.
     * @param status The response status.
     */
    public void onResponseHeaders(ChannelHandlerContext ctx, HttpHeaders headers, String status) {
        for (IndexedLogElement element: onResponseHeadersElements) {
            elements[element.index] = escape(element.onResponseHeaders(ctx, headers, status));
        }
    }

    /**
     * Triggers LogElements for the ON_RESPONSE_WRITE event.
     * @param bytesSent The number of bytes sent.
     */
    public void onResponseWrite(int bytesSent) {
        for (IndexedLogElement element: onResponseWriteElements) {
            element.onResponseWrite(bytesSent);
        }
    }

    /**
     * Triggers LogElements for the ON_LAST_RESPONSE_WRITE event.
     * @param bytesSent The number of bytes sent.
     */
    public void onLastResponseWrite(int bytesSent) {
        for (IndexedLogElement element: onLastResponseWriteElements) {
            elements[element.index] = element.onLastResponseWrite(bytesSent);
        }
    }

    /**
     * Logs at info level the accumulated values.
     *
     * @param accessLogger A logger.
     */
    public void log(Logger accessLogger) {
        if (accessLogger.isInfoEnabled()) {
            final StringBuilder b = new StringBuilder(elements.length * 5);
            for (int i = 0; i < elements.length; ++i) {
                b.append(elements[i] == null ? ConstantElement.UNKNOWN_VALUE : elements[i]);
            }
            accessLogger.info(b.toString());
        }
    }

    /**
     * Escapes the control characters (C0, DEL and C1) of a request or response derived value,
     * so that a client cannot inject line breaks or terminal escape sequences into the access log.
     *
     * @param value The value
     * @return The value with each control character replaced by a {@code \xHH} escape
     */
    static @Nullable String escape(@Nullable String value) {
        if (value == null) {
            return null;
        }
        int length = value.length();
        int i = 0;
        while (i < length && !isControl(value.charAt(i))) {
            i++;
        }
        if (i == length) {
            return value;
        }
        StringBuilder builder = new StringBuilder(length + 8);
        builder.append(value, 0, i);
        for (; i < length; i++) {
            char c = value.charAt(i);
            if (isControl(c)) {
                builder.append("\\x").append(Character.forDigit(c >> 4, 16)).append(Character.forDigit(c & 0xF, 16));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static boolean isControl(char c) {
        return c < 0x20 || (c >= 0x7F && c <= 0x9F);
    }

    private void resetIndexedLogElement(IndexedLogElement elt) {
        elements[elt.index] = null;
        elt.reset();
    }
}
