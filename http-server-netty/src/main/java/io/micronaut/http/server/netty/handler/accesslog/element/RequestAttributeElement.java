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
import io.micronaut.http.server.netty.handler.accesslog.HttpAccessLogHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;

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
        if (ctx.channel().attr(HttpAccessLogHandler.RESPONSE_REQUEST).get() instanceof HttpRequest<?> request) {
            return wrapValue(request.getAttribute(attribute).map(Object::toString).orElse(null));
        }
        return ConstantElement.UNKNOWN_VALUE;
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
