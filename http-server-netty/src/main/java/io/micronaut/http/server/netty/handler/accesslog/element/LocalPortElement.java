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

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * LocalPortElement LogElement. The local port.
 *
 * @author croudet
 * @since 2.0
 */
final class LocalPortElement implements LogElement {
    /**
     * The local port marker.
     */
    public static final String LOCAL_PORT = "p";

    /**
     * The LocalPortElement instance.
     */
    static final LocalPortElement INSTANCE = new LocalPortElement();

    private LocalPortElement() {

    }

    @Override
    public Set<Event> events() {
        return Event.REQUEST_HEADERS_EVENTS;
    }

    @Override
    public String onRequestHeaders(SocketChannel channel, String method, HttpHeaders headers, String uri, String protocol) {
        return Integer.toString(channel.localAddress().getPort());
    }

    @Override
    public LogElement copy() {
        return this;
    }

    @Override
    public String toString() {
        return '%' + LOCAL_PORT;
    }
}
