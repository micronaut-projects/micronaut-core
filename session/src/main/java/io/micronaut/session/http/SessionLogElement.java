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
package io.micronaut.session.http;

import io.micronaut.http.server.netty.handler.accesslog.element.ConstantElement;
import io.micronaut.http.server.netty.handler.accesslog.element.LogElement;

import java.util.Set;

import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.session.Session;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

/**
 * SessionLogElement LogElement. The session.
 *
 * @author croudet
 * @since 2.0
 */
public class SessionLogElement implements LogElement {
    /**
     * The session marker.
     */
    public static final String SESSION = "u";

    @SuppressWarnings("rawtypes")
    private static final AttributeKey<NettyHttpRequest> KEY = AttributeKey.valueOf(NettyHttpRequest.class.getSimpleName());

    private final String property;

    /**
     * Creates a SessionElement.
     *
     * @param property A property stored in the Session or null. When property is null the session id will be printed.
     */
    SessionLogElement(String property) {
        this.property = property;
    }

    @Override
    public LogElement copy() {
        return this;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public String onResponseHeaders(ChannelHandlerContext ctx, HttpHeaders headers, String status) {
        final Attribute<NettyHttpRequest> attr = ctx.channel().attr(KEY);
        NettyHttpRequest request = attr.get();
        if (request == null) {
            return ConstantElement.UNKNOWN_VALUE;
        }
        return SessionForRequest.find(request).map(this::value).orElse(ConstantElement.UNKNOWN_VALUE);
    }

    private String value(Session session) {
        return property == null ? session.getId() : session.get(property).map(Object::toString).orElse(ConstantElement.UNKNOWN_VALUE);
    }

    @Override
    public Set<Event> events() {
        return Event.RESPONSE_HEADERS_EVENTS;
    }

    @Override
    public String toString() {
        return '%' + SESSION;
    }
}
