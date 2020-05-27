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

import java.util.Set;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * ResponseCodeElement LogElement. The response code.
 *
 * @author croudet
 * @since 2.0
 */
final class ResponseCodeElement implements LogElement {
    /**
     * The response code marker.
     */
    public static final String RESPONSE_CODE = "s";

    /**
     * The ResponseCodeElement instance.
     */
    static final ResponseCodeElement INSTANCE = new ResponseCodeElement();

    private ResponseCodeElement() {

    }

    @Override
    public LogElement copy() {
        return this;
    }

    @Override
    public String onResponseHeaders(ChannelHandlerContext ctx, HttpHeaders headers, String status) {
        return status;
    }

    @Override
    public Set<Event> events() {
        return Event.RESPONSE_HEADERS_EVENTS;
    }

    @Override
    public String toString() {
        return '%' + RESPONSE_CODE;
    }
}
