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

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.Set;

/**
 * RequestUriElement LogElement. The request uri.
 *
 * @author croudet
 * @since 2.0
 */
final class RequestUriElement implements LogElement {
    public static final String REQUEST_URI = "x";
    public static final String REQUEST_PATH = "U";

    static final RequestUriElement INSTANCE = new RequestUriElement(false);
    static final RequestUriElement PATH_INSTANCE = new RequestUriElement(true);

    private final boolean pathOnly;

    private RequestUriElement(boolean pathOnly) {
        this.pathOnly = pathOnly;
    }

    @Override
    public LogElement copy() {
        return this;
    }

    @Override
    public String onRequestHeaders(SocketChannel channel, String method, HttpHeaders headers, String uri, String protocol) {
        return pathOnly ? requestPath(uri) : uri;
    }

    private static String requestPath(String uri) {
        int queryStart = uri.indexOf('?');
        if (queryStart == -1) {
            return uri;
        }
        return uri.substring(0, queryStart);
    }

    @Override
    public Set<Event> events() {
        return Event.REQUEST_HEADERS_EVENTS;
    }

    @Override
    public String toString() {
        return '%' + (pathOnly ? REQUEST_PATH : REQUEST_URI);
    }
}
