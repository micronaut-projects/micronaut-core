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

import java.util.Map.Entry;
import java.util.StringJoiner;

import io.netty.handler.codec.http.HttpHeaders;

/**
 * HeadersElement LogElement. All http headers.
 *
 * @author croudet
 * @since 2.0
 */
final class HeadersElement extends AbstractHttpMessageLogElement {

    private static final HeadersElement REQUEST_HEADERS_ELEMENT = new HeadersElement(true);
    private static final HeadersElement RESPONSE_HEADERS_ELEMENT = new HeadersElement(false);

    private HeadersElement(boolean onRequest) {
        this.events = onRequest ? Event.REQUEST_HEADERS_EVENTS : Event.RESPONSE_HEADERS_EVENTS;
    }

    /**
     * Returns the HeadersElement for request.
     * @return The HeadersElement for request.
     */
    public static HeadersElement forRequest() {
        return REQUEST_HEADERS_ELEMENT;
    }

    /**
     * Returns the HeadersElement for response.
     * @return The HeadersElement for response.
     */
    public static HeadersElement forResponse() {
        return RESPONSE_HEADERS_ELEMENT;
    }

    @Override
    protected String value(HttpHeaders headers) {
        if (headers.isEmpty()) {
            return ConstantElement.UNKNOWN_VALUE;
        }
        if (headers.size() == 1) {
            final Entry<CharSequence, CharSequence> header = headers.iteratorCharSequence().next();
            return header.getKey() + ":" + header.getValue();
        }
        final StringJoiner joiner = new StringJoiner(",", "[", "]");
        headers.forEach(header -> joiner.add(header.getKey() + ':' + header.getValue()));
        return joiner.toString();
    }

    @Override
    public String toString() {
        return events.contains(Event.ON_REQUEST_HEADERS) ? HeaderElement.REQUEST_HEADER : HeaderElement.RESPONSE_HEADER;
    }
}
