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
package io.micronaut.http.server.netty.handler.accesslog.element;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import io.micronaut.http.server.netty.handler.accesslog.element.AccessLogFormatParser.IndexedLogElement;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * An Access log instance.
 *
 * @author croudet
 * @since 2.0
 */
public class AccessLog {
    private List<IndexedLogElement> onRequestHeadersElements = new ArrayList<>();
    private List<IndexedLogElement> onResponseHeadersElements = new ArrayList<>();
    private List<IndexedLogElement> onResponseWriteElements = new ArrayList<>();
    private List<IndexedLogElement> onLastResponseWriteElements = new ArrayList<>();
    private String[] elements;

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
    * @param channel The socket channel.
    * @param method The http method.
    * @param headers The request headers.
    * @param uri The uri.
    * @param protocol The protocol.
    */
    public void onRequestHeaders(SocketChannel channel, String method, HttpHeaders headers, String uri, String protocol) {
        for (IndexedLogElement element: onRequestHeadersElements) {
            elements[element.index] = element.onRequestHeaders(channel, method, headers, uri, protocol);
        }
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
            elements[element.index] = element.onResponseHeaders(ctx, headers, status);
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
        final StringBuilder b = new StringBuilder(elements.length * 5);
        for (int i = 0; i < elements.length; ++i) {
            b.append(elements[i] == null ? ConstantElement.UNKNOWN_VALUE : elements[i]);
        }
        accessLogger.info(b.toString());
    }

    private void resetIndexedLogElement(IndexedLogElement elt) {
        elements[elt.index] = null;
        elt.reset();
    }
}
