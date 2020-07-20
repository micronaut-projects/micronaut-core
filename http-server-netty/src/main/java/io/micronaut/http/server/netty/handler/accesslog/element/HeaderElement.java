/*
 * Copyright 2017-2020 original authors
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

import java.util.List;
import java.util.StringJoiner;

import io.netty.handler.codec.http.HttpHeaders;

/**
 * HeaderElement LogElement. A http header.
 *
 * @author croudet
 * @since 2.0
 */
final class HeaderElement extends AbstractHttpMessageLogElement {
    /**
     * The request header marker.
     */
    public static final String REQUEST_HEADER = "i";
    /**
     * The response header marker.
     */
    public static final String RESPONSE_HEADER = "o";

    private final String header;

    /**
     * Creates a HeaderElement.
     *
     * @param onRequest When true, retrieves header from request, otherwise from response.
     * @param header The header name.
     */
    HeaderElement(boolean onRequest, final String header) {
        this.header = header;
        this.events = onRequest ? Event.REQUEST_HEADERS_EVENTS : Event.RESPONSE_HEADERS_EVENTS;
    }

    @Override
    protected String value(HttpHeaders headers) {
        final List<String> values = headers.getAllAsString(header);
        if (values.isEmpty()) {
            return ConstantElement.UNKNOWN_VALUE;
        }
        if (values.size() == 1) {
            return values.iterator().next();
        }
        final StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (String v: values) {
            joiner.add(v);
        }
        return joiner.toString();
    }

    @Override
    public String toString() {
        return "%{" + header + '}' + (events.contains(Event.ON_REQUEST_HEADERS) ? REQUEST_HEADER : RESPONSE_HEADER);
    }
}
